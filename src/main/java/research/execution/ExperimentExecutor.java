package research.execution;

import research.metrics.BenchmarkResult;

public interface ExperimentExecutor {
    String architectureName();
    BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes);
}
