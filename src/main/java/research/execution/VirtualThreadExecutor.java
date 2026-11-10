package research.execution;

import research.benchmarks.BenchmarkWorkload;
import research.metrics.BenchmarkResult;
import research.metrics.BenchmarkMeasurements;
import research.mpi.MpiBenchmarkSupport;
import research.mpi.MpiRuntime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public final class VirtualThreadExecutor implements ExperimentExecutor {
    @Override
    public String architectureName() {
        return "C";
    }

    @Override
    public BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes) {
        BenchmarkMeasurements measurements = BenchmarkMeasurements.start();
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicLong checksum = new AtomicLong(0L);
        Thread[] workers = new Thread[taskCount];

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            long submittedNanos = measurements.taskSubmitted();
            workers[i] = ExecutorSupport.startVirtualThread(() -> {
                measurements.taskStarted(submittedNanos);
                long partial = BenchmarkWorkload.computeTask(
                        benchmarkName, taskId, ranks, messageSizeBytes,
                        MpiRuntime.isActive() ? MpiRuntime.rank() : 0, taskCount);
                checksum.addAndGet(partial);
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Virtual-thread execution interrupted", ex);
        }

        MpiBenchmarkSupport.ExecutionResult communication =
                MpiBenchmarkSupport.execute(benchmarkName, checksum.get(), taskCount, messageSizeBytes);
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
        extras.put("mode", "virtual-threads");
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
        return new BenchmarkResult("C", benchmarkName, ranks, taskCount, messageSizeBytes,
                durationMs, throughput,
                communication.communicationNanos() / (double) elapsedNanos,
                aggregated.cpuUtilization(), aggregated.queueWaitMs(), extras);
    }
}
