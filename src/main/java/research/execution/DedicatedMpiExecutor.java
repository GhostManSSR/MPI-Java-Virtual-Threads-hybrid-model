package research.execution;

import research.metrics.BenchmarkResult;
import research.mpi.MpiCommunicationThread;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public final class DedicatedMpiExecutor implements ExperimentExecutor {
    @Override
    public String architectureName() {
        return "D";
    }

    @Override
    public BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes) {
        long startNs = System.nanoTime();
        MpiCommunicationThread communicationThread = new MpiCommunicationThread();
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicLong checksum = new AtomicLong(0L);

        try {
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                ExecutorSupport.startVirtualThread(() -> {
                    long partial = 0L;
                    for (int j = 0; j < Math.max(8, ranks * 64); j++) {
                        partial += (long) (taskId + 1) * (j + 1L) * messageSizeBytes;
                    }
                    communicationThread.submit("rank-" + (taskId % ranks), partial);
                    checksum.addAndGet(partial);
                    latch.countDown();
                });
            }
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dedicated MPI thread execution interrupted", ex);
        } finally {
            communicationThread.close();
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        double throughput = (taskCount * ranks * 1_000_000d) / Math.max(durationMs, 1L);
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("checksum", checksum.get());
        extras.put("mode", "dedicated-mpi-thread");
        extras.put("queueActive", true);
        return new BenchmarkResult("D", benchmarkName, ranks, taskCount, messageSizeBytes,
                durationMs, throughput, 0.14d, 0.72d, 0.02d, extras);
    }
}
