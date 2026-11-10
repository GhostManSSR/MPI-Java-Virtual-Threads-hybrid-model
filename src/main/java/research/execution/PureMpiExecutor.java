package research.execution;

import research.benchmarks.BenchmarkWorkload;
import research.metrics.BenchmarkResult;
import research.metrics.BenchmarkMeasurements;
import research.mpi.MpiBenchmarkSupport;
import research.mpi.MpiRuntime;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PureMpiExecutor implements ExperimentExecutor {
    @Override
    public String architectureName() {
        return "A";
    }

    @Override
    public BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes) {
        BenchmarkMeasurements measurements = BenchmarkMeasurements.start();
        long checksum = 0L;
        for (int i = 0; i < taskCount; i++) {
            checksum += BenchmarkWorkload.computeTask(
                    benchmarkName, i, ranks, messageSizeBytes, MpiRuntime.isActive() ? MpiRuntime.rank() : 0, taskCount);
        }
        MpiBenchmarkSupport.ExecutionResult communication =
                MpiBenchmarkSupport.execute(benchmarkName, checksum, taskCount, messageSizeBytes);
        BenchmarkMeasurements.Snapshot localMeasurements = measurements.finish();
        BenchmarkMeasurements.Aggregated aggregated = BenchmarkMeasurements.aggregate(
                localMeasurements, MpiRuntime.isActive(),
                MpiRuntime::maximumElapsedNanos, MpiRuntime::sumLong);
        long elapsedNanos = aggregated.elapsedNanos();
        double durationMs = elapsedNanos / 1_000_000d;
        int worldSize = MpiRuntime.isActive() ? MpiRuntime.size() : 1;
        double throughput = (taskCount * (double) worldSize * 1_000_000_000d) / elapsedNanos;
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("checksum", communication.value());
        extras.put("mode", "pure-mpi");
        extras.put("elapsedNanos", elapsedNanos);
        extras.put("rankElapsedNanos", localMeasurements.elapsedNanos());
        extras.put("rankProcessCpuNanos", localMeasurements.processCpuNanos());
        extras.put("aggregateProcessCpuNanos", aggregated.processCpuNanos());
        extras.put("availableProcessors", localMeasurements.availableProcessors());
        extras.put("cpuCapacityProcessors", aggregated.availableProcessors());
        extras.put("rankQueueWaitNanos", localMeasurements.meanQueueWaitNanos());
        extras.put("communicationCalls", communication.communicationCalls());
        extras.put("communicationBytesPerRank", communication.communicationBytes());
        extras.put("mpiEnabled", MpiRuntime.isActive());
        extras.put("mpiRank", MpiRuntime.isActive() ? MpiRuntime.rank() : 0);
        extras.put("mpiWorldSize", worldSize);
        extras.put("cpuUtilizationMeasured", true);
        extras.put("queueWaitMeasured", true);
        if (MpiRuntime.isActive()) {
            extras.put("mpiThreadSupport", MpiRuntime.providedLevel());
        }
        return new BenchmarkResult("A", benchmarkName, ranks, taskCount, messageSizeBytes,
                durationMs, throughput,
                communication.communicationNanos() / (double) elapsedNanos,
                aggregated.cpuUtilization(), aggregated.queueWaitMs(), extras);
    }
}
