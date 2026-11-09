package research.execution;

import research.metrics.BenchmarkResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlatformThreadExecutor implements ExperimentExecutor {
    @Override
    public String architectureName() {
        return "B";
    }

    @Override
    public BenchmarkResult run(String benchmarkName, int ranks, int taskCount, int messageSizeBytes) {
        long startNs = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(taskCount, Runtime.getRuntime().availableProcessors() * 2)));
        CountDownLatch latch = new CountDownLatch(taskCount);
        long[] checksum = new long[1];

        try {
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    long partial = 0L;
                    for (int j = 0; j < Math.max(8, ranks * 64); j++) {
                        partial += (long) (taskId + 1) * (j + 1L) * messageSizeBytes;
                    }
                    synchronized (PlatformThreadExecutor.class) {
                        checksum[0] += partial;
                    }
                    latch.countDown();
                });
            }
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Platform-thread execution interrupted", ex);
        } finally {
            executor.shutdownNow();
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        double throughput = (taskCount * ranks * 1_000_000d) / Math.max(durationMs, 1L);
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("checksum", checksum[0]);
        extras.put("mode", "platform-threads");
        return new BenchmarkResult("B", benchmarkName, ranks, taskCount, messageSizeBytes,
                durationMs, throughput, 0.18d, 0.81d, 0.03d, extras);
    }
}
