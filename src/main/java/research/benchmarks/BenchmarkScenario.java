package research.benchmarks;

import research.execution.ExperimentExecutor;
import research.metrics.BenchmarkResult;

public interface BenchmarkScenario {
    String name();
    BenchmarkResult execute(ExperimentExecutor executor, int ranks, int taskCount, int messageSizeBytes);
}
