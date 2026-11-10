package research.benchmarks;

public final class BenchmarkWorkload {
    private BenchmarkWorkload() {
    }

    public static long computeTask(
            String benchmarkName,
            int taskId,
            int ranks,
            int messageSizeBytes,
            int rank,
            int taskCount) {
        if ("montecarlo".equals(benchmarkName)) {
            long seed = ((long) rank * taskCount + taskId + 1L) * 0x9E3779B97F4A7C15L;
            long xBits = mix64(seed);
            long yBits = mix64(seed + 0x632BE59BD9B4E019L);
            double x = toUnitInterval(xBits);
            double y = toUnitInterval(yBits);
            return x * x + y * y <= 1.0d ? 1L : 0L;
        }

        long partial = 0L;
        int iterations = Math.max(8, ranks * 64);
        for (int j = 0; j < iterations; j++) {
            partial += (long) (taskId + 1) * (j + 1L) * messageSizeBytes;
        }
        return partial;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double toUnitInterval(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
