package research.mpi;

public final class MpiNativeBridge {
    private static final boolean LOADED;

    static {
        boolean loadResult = false;
        try {
            System.loadLibrary("mpi_bridge");
            loadResult = true;
        } catch (UnsatisfiedLinkError ignored) {
            // This project intentionally supports a non-JNI fallback path for local, reproducible benchmarks.
        }
        LOADED = loadResult;
    }

    private MpiNativeBridge() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void initialize(String requestedThreadLevel) {
        if (!LOADED) {
            return;
        }
        mpiInitThread(requestedThreadLevel);
    }

    public static native void mpiInitThread(String requestedThreadLevel);
}
