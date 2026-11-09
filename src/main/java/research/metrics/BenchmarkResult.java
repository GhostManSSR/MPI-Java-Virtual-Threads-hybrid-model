package research.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

public record BenchmarkResult(
        String architecture,
        String benchmark,
        int ranks,
        int taskCount,
        int messageSizeBytes,
        long durationMs,
        double throughput,
        double communicationRatio,
        double cpuUtilization,
        double queueWaitMs,
        Map<String, Object> extras
) {
    public BenchmarkResult {
        extras = extras == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extras);
    }

    public static String csvHeader() {
        return String.join(",",
                "architecture",
                "benchmark",
                "ranks",
                "taskCount",
                "messageSizeBytes",
                "durationMs",
                "throughput",
                "communicationRatio",
                "cpuUtilization",
                "queueWaitMs",
                "extra");
    }

    public String toCsvRow() {
        return String.join(",",
                architecture,
                benchmark,
                String.valueOf(ranks),
                String.valueOf(taskCount),
                String.valueOf(messageSizeBytes),
                String.valueOf(durationMs),
                String.format(java.util.Locale.US, "%.3f", throughput),
                String.format(java.util.Locale.US, "%.3f", communicationRatio),
                String.format(java.util.Locale.US, "%.3f", cpuUtilization),
                String.format(java.util.Locale.US, "%.3f", queueWaitMs),
                extras.toString().replace(',', ';'));
    }
}
