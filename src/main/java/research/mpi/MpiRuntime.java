package research.mpi;

public final class MpiRuntime {
    public enum ThreadSupportLevel {
        SINGLE(0),
        FUNNELED(1),
        SERIALIZED(2),
        MULTIPLE(3);

        private final int rank;

        ThreadSupportLevel(int rank) {
            this.rank = rank;
        }

        public int ordinalRank() {
            return rank;
        }
    }

    private MpiRuntime() {
    }

    public static ThreadSupportLevel detectProvidedLevel() {
        String configured = System.getenv("MPI_THREAD_LEVEL");
        if (configured == null || configured.isBlank()) {
            return ThreadSupportLevel.MULTIPLE;
        }

        String normalized = configured.trim().toUpperCase();
        switch (normalized) {
            case "MPI_THREAD_SINGLE":
                return ThreadSupportLevel.SINGLE;
            case "MPI_THREAD_FUNNELED":
                return ThreadSupportLevel.FUNNELED;
            case "MPI_THREAD_SERIALIZED":
                return ThreadSupportLevel.SERIALIZED;
            case "MPI_THREAD_MULTIPLE":
                return ThreadSupportLevel.MULTIPLE;
            default:
                return ThreadSupportLevel.MULTIPLE;
        }
    }

    public static void validateSupportedLevel(ThreadSupportLevel requiredLevel) {
        ThreadSupportLevel providedLevel = detectProvidedLevel();
        if (providedLevel.ordinalRank() < requiredLevel.ordinalRank()) {
            throw new IllegalStateException(
                    "MPI runtime does not provide the required thread support level: required=" + requiredLevel + ", provided=" + providedLevel);
        }
    }

    public static String describe() {
        return "MPI thread support: " + detectProvidedLevel();
    }
}
