package research.execution;

import research.benchmarks.BenchmarkWorkload;
import research.metrics.BenchmarkResult;
import research.metrics.BenchmarkMeasurements;
import research.mpi.MpiCommunicationThread;
import research.mpi.MpiRuntime;
import research.mpi.MpiBenchmarkSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.LongUnaryOperator;

public final class DedicatedMpiExecutor implements ExperimentExecutor {
    @Override
    public String architectureName() {
        return "D";
    }

    @Override
    public BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes) {
        BenchmarkMeasurements measurements = BenchmarkMeasurements.start();
        MpiCommunicationThread communicationThread = new MpiCommunicationThread();
        CountDownLatch latch = new CountDownLatch(taskCount);
        long[] checksum = new long[1];

        try {
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                long submittedNanos = measurements.taskSubmitted();
                ExecutorSupport.startVirtualThread(() -> {
                    measurements.taskStarted(submittedNanos);
                    long partial = BenchmarkWorkload.computeTask(
                            benchmarkName, taskId, ranks, messageSizeBytes,
                            MpiRuntime.isActive() ? MpiRuntime.rank() : 0, taskCount);
                    synchronized (checksum) {
                        checksum[0] += partial;
                    }
                    latch.countDown();
                });
            }
            latch.await();
            long localChecksum = checksum[0];
            MpiBenchmarkSupport.ExecutionResult communication = communicationThread.submit(
                    () -> MpiBenchmarkSupport.execute(benchmarkName, localChecksum, taskCount, messageSizeBytes));
            BenchmarkMeasurements.Snapshot localMeasurements = measurements.finish();
            LongUnaryOperator maximum = value -> communicationThread.submit(
                    () -> MpiRuntime.maximumElapsedNanos(value));
            LongUnaryOperator sum = value -> communicationThread.submit(() -> MpiRuntime.sumLong(value));
            BenchmarkMeasurements.Aggregated aggregated = BenchmarkMeasurements.aggregate(
                    localMeasurements, MpiRuntime.isActive(), maximum, sum);
            long elapsedNanos = aggregated.elapsedNanos();
            double durationMs = elapsedNanos / 1_000_000d;
            int worldSize = MpiRuntime.isActive() ? MpiRuntime.size() : 1;
            double throughput = (taskCount * (double) worldSize * 1_000_000_000d) / elapsedNanos;
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("checksum", communication.value());
            extras.put("mode", "dedicated-mpi-thread");
            extras.put("queueActive", true);
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
            return new BenchmarkResult("D", benchmarkName, ranks, taskCount, messageSizeBytes,
                    durationMs, throughput,
                    communication.communicationNanos() / (double) elapsedNanos,
                    aggregated.cpuUtilization(), aggregated.queueWaitMs(), extras);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dedicated MPI thread execution interrupted", ex);
        } finally {
            communicationThread.close();
        }
    }
}
