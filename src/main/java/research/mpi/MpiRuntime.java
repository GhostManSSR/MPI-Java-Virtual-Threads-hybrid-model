package research.mpi;

import research.execution.ExecutionArchitecture;

public final class MpiRuntime implements AutoCloseable {
    public enum ThreadSupportLevel {
        SINGLE("MPI_THREAD_SINGLE", 0),
        FUNNELED("MPI_THREAD_FUNNELED", 1),
        SERIALIZED("MPI_THREAD_SERIALIZED", 2),
        MULTIPLE("MPI_THREAD_MULTIPLE", 3);

        private final String mpiName;
        private final int rank;

        ThreadSupportLevel(String mpiName, int rank) {
            this.mpiName = mpiName;
            this.rank = rank;
        }

        public int ordinalRank() {
            return rank;
        }

        public String mpiName() {
            return mpiName;
        }

        public static ThreadSupportLevel fromOrdinalRank(int rank) {
            for (ThreadSupportLevel level : values()) {
                if (level.rank == rank) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unsupported MPI thread support value: " + rank);
        }
    }

    private static volatile MpiRuntime activeRuntime;

    private final int rank;
    private final int size;
    private final ThreadSupportLevel providedLevel;
    private boolean closed;

    private MpiRuntime(int rank, int size, ThreadSupportLevel providedLevel) {
        this.rank = rank;
        this.size = size;
        this.providedLevel = providedLevel;
    }

    public static synchronized MpiRuntime initialize(ExecutionArchitecture architecture) {
        if (activeRuntime != null) {
            throw new IllegalStateException("MPI runtime is already initialized in this process");
        }

        ThreadSupportLevel requiredLevel = requiredThreadLevel(architecture);
        int providedRank = MpiNativeBridge.initialize(requiredLevel.mpiName());
        try {
            ThreadSupportLevel providedLevel = ThreadSupportLevel.fromOrdinalRank(providedRank);
            int queriedRank = MpiNativeBridge.mpiQueryThread();
            if (queriedRank != providedRank) {
                throw new IllegalStateException(
                        "MPI thread support changed after initialization: init=" + providedLevel
                                + ", query=" + ThreadSupportLevel.fromOrdinalRank(queriedRank));
            }
            validateSupportedLevel(requiredLevel, providedLevel);

            int rank = MpiNativeBridge.mpiRank();
            int size = MpiNativeBridge.mpiSize();
            if (size < 2) {
                throw new IllegalStateException(
                        "MPI benchmark requires at least two ranks; MPI world size is " + size);
            }

            MpiRuntime runtime = new MpiRuntime(rank, size, providedLevel);
            activeRuntime = runtime;
            return runtime;
        } catch (RuntimeException ex) {
            MpiNativeBridge.mpiFinalize();
            throw ex;
        }
    }

    public static ThreadSupportLevel requiredThreadLevel(ExecutionArchitecture architecture) {
        return switch (architecture) {
            case A -> ThreadSupportLevel.SINGLE;
            case B, C -> ThreadSupportLevel.FUNNELED;
            case D -> ThreadSupportLevel.MULTIPLE;
        };
    }

    public static ThreadSupportLevel detectProvidedLevel() {
        String configured = System.getenv("MPI_THREAD_LEVEL");
        if (configured == null || configured.isBlank()) {
            return ThreadSupportLevel.MULTIPLE;
        }

        String normalized = configured.trim().toUpperCase();
        for (ThreadSupportLevel level : ThreadSupportLevel.values()) {
            if (level.mpiName().equals(normalized)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unsupported MPI_THREAD_LEVEL: " + configured);
    }

    public static void validateSupportedLevel(ThreadSupportLevel requiredLevel) {
        validateSupportedLevel(requiredLevel, detectProvidedLevel());
    }

    private static void validateSupportedLevel(
            ThreadSupportLevel requiredLevel,
            ThreadSupportLevel providedLevel) {
        if (providedLevel.ordinalRank() < requiredLevel.ordinalRank()) {
            throw new IllegalStateException(
                    "MPI runtime does not provide the required thread support level: required="
                            + requiredLevel + ", provided=" + providedLevel);
        }
    }

    public static boolean isActive() {
        return activeRuntime != null;
    }

    public static int rank() {
        return requireActive().rank;
    }

    public static int size() {
        return requireActive().size;
    }

    public static ThreadSupportLevel providedLevel() {
        return requireActive().providedLevel;
    }

    public static boolean isRoot() {
        return rank() == 0;
    }

    public static void synchronizeRanks() {
        requireActive();
        MpiNativeBridge.mpiBarrier();
    }

    public static long maximumElapsedNanos(long localElapsedNanos) {
        requireActive();
        return MpiNativeBridge.mpiMaxLong(localElapsedNanos);
    }

    public static long sumLong(long localValue) {
        requireActive();
        return MpiNativeBridge.mpiSumLong(localValue);
    }

    public static String describe() {
        MpiRuntime runtime = activeRuntime;
        if (runtime == null) {
            return "MPI is not initialized";
        }
        return "MPI rank " + runtime.rank + "/" + runtime.size
                + ", provided thread support: " + runtime.providedLevel;
    }

    private static MpiRuntime requireActive() {
        MpiRuntime runtime = activeRuntime;
        if (runtime == null) {
            throw new IllegalStateException("MPI operation requested outside an initialized MPI run");
        }
        return runtime;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        MpiNativeBridge.mpiFinalize();
        closed = true;
        activeRuntime = null;
    }
}
