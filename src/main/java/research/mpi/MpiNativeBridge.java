package research.mpi;

public final class MpiNativeBridge {
    private static final String LOAD_ERROR;

    static {
        String error = null;
        try {
            System.loadLibrary("mpi_bridge");
        } catch (UnsatisfiedLinkError ex) {
            error = ex.getMessage();
        }
        LOAD_ERROR = error;
    }

    private MpiNativeBridge() {
    }

    public static boolean isLoaded() {
        return LOAD_ERROR == null;
    }

    public static void requireLoaded() {
        if (!isLoaded()) {
            throw new IllegalStateException(
                    "MPI JNI bridge is unavailable. Build src/main/c/mpi_bridge.c with mpicc and set "
                            + "java.library.path to target/native. Load error: " + LOAD_ERROR);
        }
    }

    public static int initialize(String requestedThreadLevel) {
        requireLoaded();
        return mpiInitThread(requestedThreadLevel);
    }

    public static native int mpiInitThread(String requestedThreadLevel);

    public static native int mpiRank();

    public static native int mpiSize();

    public static native int mpiQueryThread();

    public static native void mpiBarrier();

    public static native long mpiMaxLong(long value);

    public static native long mpiSumLong(long value);

    public static native void mpiSendRecvBytes(byte[] send, byte[] receive, int destination, int source, int tag);

    public static native void mpiAllreduceSumLongArray(long[] values);

    public static native long[] mpiExchangeHalos(long leftSend, long rightSend, int leftRank, int rightRank);

    public static native void mpiFinalize();
}
