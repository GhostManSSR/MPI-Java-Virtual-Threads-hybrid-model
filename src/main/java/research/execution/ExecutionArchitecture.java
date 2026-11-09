package research.execution;

public enum ExecutionArchitecture {
    A("Pure MPI"),
    B("MPI + Platform Threads"),
    C("MPI + Virtual Threads"),
    D("MPI + Virtual Threads + Dedicated MPI Thread");

    private final String label;

    ExecutionArchitecture(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ExecutionArchitecture fromValue(String value) {
        if (value == null) {
            return A;
        }
        String normalized = value.trim().toUpperCase();
        switch (normalized) {
            case "A":
                return A;
            case "B":
                return B;
            case "C":
                return C;
            case "D":
                return D;
            case "ALL":
                return A;
            default:
                throw new IllegalArgumentException("Unsupported architecture: " + value);
        }
    }
}
