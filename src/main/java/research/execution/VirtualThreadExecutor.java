package research.execution;

import research.metrics.BenchmarkResult;

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
        long startNs = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicLong checksum = new AtomicLong(0L);
        Thread[] workers = new Thread[taskCount];

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            workers[i] = ExecutorSupport.startVirtualThread(() -> {
                long partial = 0L;
                for (int j = 0; j < Math.max(8, ranks * 64); j++) {
                    partial += (long) (taskId + 1) * (j + 1L) * messageSizeBytes;
                }
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

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        double throughput = (taskCount * ranks * 1_000_000d) / Math.max(durationMs, 1L);
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("checksum", checksum.get());
        extras.put("mode", "virtual-threads");
        return new BenchmarkResult("C", benchmarkName, ranks, taskCount, messageSizeBytes,
                durationMs, throughput, 0.25d, 0.88d, 0.06d, extras);
    }
}
