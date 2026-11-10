package research.metrics;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongUnaryOperator;

public final class BenchmarkMeasurements {
    private static final OperatingSystemMXBean OPERATING_SYSTEM = operatingSystemBean();

    private final long startNanos;
    private final long startProcessCpuNanos;
    private final int availableProcessors;
    private final LongAdder queueWaitNanos = new LongAdder();
    private final AtomicLong queueWaitSamples = new AtomicLong();

    private BenchmarkMeasurements() {
        startProcessCpuNanos = readProcessCpuTime();
        startNanos = System.nanoTime();
        availableProcessors = Runtime.getRuntime().availableProcessors();
    }

    public static BenchmarkMeasurements start() {
        return new BenchmarkMeasurements();
    }

    public long taskSubmitted() {
        return System.nanoTime();
    }

    public void taskStarted(long submittedNanos) {
        long waitNanos = System.nanoTime() - submittedNanos;
        if (waitNanos < 0) {
            throw new IllegalStateException("Task start time preceded its submission time");
        }
        queueWaitNanos.add(waitNanos);
        queueWaitSamples.incrementAndGet();
    }

    public Snapshot finish() {
        long elapsedNanos = System.nanoTime() - startNanos;
        long processCpuNanos = readProcessCpuTime() - startProcessCpuNanos;
        if (elapsedNanos <= 0 || processCpuNanos < 0) {
            throw new IllegalStateException(
                    "Invalid benchmark measurements: elapsed=" + elapsedNanos
                            + ", processCpu=" + processCpuNanos);
        }
        long samples = queueWaitSamples.get();
        long meanQueueWaitNanos = samples == 0 ? 0 : queueWaitNanos.sum() / samples;
        return new Snapshot(elapsedNanos, processCpuNanos, availableProcessors, meanQueueWaitNanos);
    }

    public static Aggregated aggregate(
            Snapshot local,
            boolean mpiActive,
            LongUnaryOperator maximum,
            LongUnaryOperator sum) {
        long elapsedNanos = mpiActive ? maximum.applyAsLong(local.elapsedNanos()) : local.elapsedNanos();
        long processCpuNanos = mpiActive ? sum.applyAsLong(local.processCpuNanos()) : local.processCpuNanos();
        long processors = mpiActive ? maximum.applyAsLong(local.availableProcessors()) : local.availableProcessors();
        long queueWaitNanos = mpiActive
                ? maximum.applyAsLong(local.meanQueueWaitNanos())
                : local.meanQueueWaitNanos();
        if (elapsedNanos <= 0 || processors <= 0) {
            throw new IllegalStateException(
                    "Invalid aggregated benchmark measurements: elapsed=" + elapsedNanos
                            + ", processors=" + processors);
        }
        double cpuUtilization = Math.min(
                1d, processCpuNanos / (double) elapsedNanos / processors);
        return new Aggregated(elapsedNanos, processCpuNanos, processors,
                queueWaitNanos, cpuUtilization);
    }

    private static OperatingSystemMXBean operatingSystemBean() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (!(bean instanceof OperatingSystemMXBean supportedBean)) {
            throw new IllegalStateException("Process CPU-time measurement is not supported by this JVM");
        }
        return supportedBean;
    }

    private static long readProcessCpuTime() {
        long cpuNanos = OPERATING_SYSTEM.getProcessCpuTime();
        if (cpuNanos < 0) {
            throw new IllegalStateException("JVM did not provide process CPU time");
        }
        return cpuNanos;
    }

    public record Snapshot(
            long elapsedNanos,
            long processCpuNanos,
            int availableProcessors,
            long meanQueueWaitNanos) {
    }

    public record Aggregated(
            long elapsedNanos,
            long processCpuNanos,
            long availableProcessors,
            long queueWaitNanos,
            double cpuUtilization) {
        public double queueWaitMs() {
            return queueWaitNanos / 1_000_000d;
        }
    }
}
