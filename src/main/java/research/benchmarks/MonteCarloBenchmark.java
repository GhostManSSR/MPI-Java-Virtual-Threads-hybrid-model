package research.benchmarks;

import research.execution.ExperimentExecutor;
import research.metrics.BenchmarkResult;

public final class MonteCarloBenchmark implements BenchmarkScenario {
    @Override
    public String name() {
        return "montecarlo";
    }

    @Override
    public BenchmarkResult execute(ExperimentExecutor executor, int ranks, int taskCount, int messageSizeBytes) {
        return executor.run(name(), ranks, taskCount, messageSizeBytes);
    }
}
