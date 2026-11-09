package research.benchmarks;

import research.execution.ExperimentExecutor;
import research.metrics.BenchmarkResult;

public final class PingPongBenchmark implements BenchmarkScenario {
    @Override
    public String name() {
        return "pingpong";
    }

    @Override
    public BenchmarkResult execute(ExperimentExecutor executor, int ranks, int taskCount, int messageSizeBytes) {
        return executor.run(name(), ranks, taskCount, messageSizeBytes);
    }
}
