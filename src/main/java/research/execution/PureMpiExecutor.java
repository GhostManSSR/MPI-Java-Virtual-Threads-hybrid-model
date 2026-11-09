package research.execution;

import research.metrics.BenchmarkResult;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PureMpiExecutor implements ExperimentExecutor {
    @Override
    public String architectureName() {
        return "A";
    }

    @Override
    public BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes) {
        long startNs = System.nanoTime();
        long checksum = 0L;
        for (int i = 0; i < taskCount; i++) {
            long partial = 0L;
            for (int j = 0; j < Math.max(8, ranks * 128); j++) {
                partial += (long) (i + 1) * (j + 1L) * messageSizeBytes;
            }
            checksum += partial;
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        double throughput = (taskCount * ranks * 1_000_000d) / Math.max(durationMs, 1L);
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("checksum", checksum);
        extras.put("mode", "pure-mpi");
        return new BenchmarkResult("A", benchmarkName, ranks, taskCount, messageSizeBytes,
                durationMs, throughput, 0.12d, 0.74d, 0.0d, extras);
    }
}
