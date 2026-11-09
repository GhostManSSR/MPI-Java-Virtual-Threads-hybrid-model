package research;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import research.benchmarks.AllReduceBenchmark;
import research.benchmarks.BenchmarkScenario;
import research.benchmarks.HeatEquationBenchmark;
import research.benchmarks.MonteCarloBenchmark;
import research.benchmarks.PingPongBenchmark;
import research.execution.DedicatedMpiExecutor;
import research.execution.ExecutionArchitecture;
import research.execution.ExperimentExecutor;
import research.execution.PlatformThreadExecutor;
import research.execution.PureMpiExecutor;
import research.execution.VirtualThreadExecutor;
import research.metrics.BenchmarkResult;
import research.metrics.MetricsExporter;
import research.mpi.MpiRuntime;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class Main {
    private static final AtomicLong LAST_DURATION_MS = new AtomicLong(0L);
    private static final AtomicLong LAST_THROUGHPUT = new AtomicLong(0L);
    private static final AtomicReference<String> LAST_ARCHITECTURE = new AtomicReference<>("A");
    private static final AtomicReference<String> LAST_SCENARIO = new AtomicReference<>("pingpong");
    private static final AtomicReference<List<BenchmarkResult>> LAST_RESULTS = new AtomicReference<>(List.of());
    private static final Map<String, String> LAST_META = new ConcurrentHashMap<>();

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || hasHelpFlag(args)) {
            printHelp();
            return;
        }

        if (hasServerFlag(args)) {
            startHttpServer();
            return;
        }

        runBenchmarkCommand(args);
    }

    private static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/health", Main::handleHealth);
        server.createContext("/metrics", Main::handleMetrics);
        server.createContext("/run", Main::handleRun);
        server.createContext("/", Main::handleRoot);
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP metrics server started on http://0.0.0.0:8080");
    }

    private static void handleRoot(HttpExchange exchange) throws IOException {
        byte[] payload = (
                "{\"status\":\"ok\",\"service\":\"mpi-virtual-threads-research\",\"routes\":[\"/health\",\"/metrics\",\"/run\"]}"
        ).getBytes(StandardCharsets.UTF_8);
        sendJson(exchange, 200, payload);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        byte[] payload = "ok".getBytes(StandardCharsets.UTF_8);
        sendText(exchange, 200, payload);
    }

    private static void handleMetrics(HttpExchange exchange) throws IOException {
        StringBuilder metrics = new StringBuilder();
        metrics.append("# HELP app_last_run_duration_ms Last benchmark duration in milliseconds\n");
        metrics.append("# TYPE app_last_run_duration_ms gauge\n");
        metrics.append("app_last_run_duration_ms ").append(LAST_DURATION_MS.get()).append("\n");
        metrics.append("# HELP app_last_run_throughput Throughput from the last benchmark run\n");
        metrics.append("# TYPE app_last_run_throughput gauge\n");
        metrics.append("app_last_run_throughput ").append(LAST_THROUGHPUT.get()).append("\n");
        metrics.append("# HELP app_last_run_architecture Architecture used during the last benchmark run\n");
        metrics.append("# TYPE app_last_run_architecture gauge\n");
        metrics.append("app_last_run_architecture{architecture=\"" + LAST_ARCHITECTURE.get() + "\"} 1\n");
        metrics.append("# HELP app_last_run_scenario Scenario used during the last benchmark run\n");
        metrics.append("# TYPE app_last_run_scenario gauge\n");
        metrics.append("app_last_run_scenario{scenario=\"" + LAST_SCENARIO.get() + "\"} 1\n");
        metrics.append("# HELP app_last_run_meta Last benchmark metadata\n");
        metrics.append("# TYPE app_last_run_meta gauge\n");
        for (Map.Entry<String, String> entry : LAST_META.entrySet()) {
            metrics.append("app_last_run_meta{key=\"" + entry.getKey() + "\"} ").append(entry.getValue()).append("\n");
        }
        metrics.append("# HELP app_benchmark_duration_ms Duration for each benchmark in the latest run\n");
        metrics.append("# TYPE app_benchmark_duration_ms gauge\n");
        metrics.append("# HELP app_benchmark_throughput Throughput for each benchmark in the latest run\n");
        metrics.append("# TYPE app_benchmark_throughput gauge\n");
        metrics.append("# HELP app_benchmark_cpu_utilization_ratio CPU utilization ratio for each benchmark in the latest run\n");
        metrics.append("# TYPE app_benchmark_cpu_utilization_ratio gauge\n");
        metrics.append("# HELP app_benchmark_communication_ratio Communication-to-computation ratio for each benchmark in the latest run\n");
        metrics.append("# TYPE app_benchmark_communication_ratio gauge\n");
        metrics.append("# HELP app_benchmark_queue_wait_ms Queue wait time for each benchmark in the latest run\n");
        metrics.append("# TYPE app_benchmark_queue_wait_ms gauge\n");
        for (BenchmarkResult result : LAST_RESULTS.get()) {
            String labels = "{architecture=\"" + result.architecture() + "\",scenario=\"" + result.benchmark() + "\"}";
            appendBenchmarkMetric(metrics, "app_benchmark_duration_ms", labels, result.durationMs());
            appendBenchmarkMetric(metrics, "app_benchmark_throughput", labels, result.throughput());
            appendBenchmarkMetric(metrics, "app_benchmark_cpu_utilization_ratio", labels, result.cpuUtilization());
            appendBenchmarkMetric(metrics, "app_benchmark_communication_ratio", labels, result.communicationRatio());
            appendBenchmarkMetric(metrics, "app_benchmark_queue_wait_ms", labels, result.queueWaitMs());
        }
        sendText(exchange, 200, metrics.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendBenchmarkMetric(StringBuilder metrics, String name, String labels, double value) {
        metrics.append(name).append(labels).append(' ')
                .append(String.format(java.util.Locale.US, "%.3f", value)).append('\n');
    }

    private static void handleRun(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            byte[] payload = "POST only".getBytes(StandardCharsets.UTF_8);
            sendText(exchange, 405, payload);
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }

        String architectureValue = params.getOrDefault("architecture", readArgFromQuery(exchange, "architecture", "all"));
        String scenarioValue = params.getOrDefault("scenario", readArgFromQuery(exchange, "scenario", "pingpong"));
        int taskCount = Integer.parseInt(params.getOrDefault("tasks", readArgFromQuery(exchange, "tasks", "128")));
        int ranks = Integer.parseInt(params.getOrDefault("ranks", readArgFromQuery(exchange, "ranks", "2")));
        int messageSizeBytes = Integer.parseInt(params.getOrDefault("message_size", readArgFromQuery(exchange, "message_size", "1024")));

        ArrayList<BenchmarkResult> results = new ArrayList<>();
        List<String> architectures = architectureValue.equalsIgnoreCase("all") ? List.of("A", "B", "C", "D") : List.of(architectureValue);
        List<BenchmarkScenario> scenarios = scenarioList(scenarioValue);

        for (String architectureId : architectures) {
            ExecutionArchitecture architecture = ExecutionArchitecture.fromValue(architectureId);
            ExperimentExecutor executor = createExecutor(architecture);
            for (BenchmarkScenario scenario : scenarios) {
                BenchmarkResult result = scenario.execute(executor, ranks, taskCount, messageSizeBytes);
                results.add(result);
            }
        }

        Path outputPath = Path.of("experiments", "results", "benchmark-results.csv");
        MetricsExporter.exportCsv(outputPath, results);

        recordLatestResults(results, taskCount, ranks, messageSizeBytes);

        String json = buildRunResponse(results, outputPath);
        sendJson(exchange, 200, json.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildRunResponse(List<BenchmarkResult> results, Path outputPath) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"status\":\"ok\",\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult result = results.get(i);
            if (i > 0) builder.append(',');
            builder.append("{\"architecture\":\"").append(result.architecture())
                    .append("\",\"benchmark\":\"").append(result.benchmark())
                    .append("\",\"durationMs\":").append(result.durationMs())
                    .append(",\"throughput\":").append(String.format(java.util.Locale.US, "%.3f", result.throughput()))
                    .append(",\"cpuUtilization\":").append(String.format(java.util.Locale.US, "%.3f", result.cpuUtilization()))
                    .append("}");
        }
        builder.append("],\"output\":\"").append(outputPath.toAbsolutePath()).append("\"}");
        return builder.toString();
    }

    private static void runBenchmarkCommand(String[] args) throws Exception {
        String architectureValue = readArg(args, "-a", "--architecture", "all");
        String scenarioValue = readArg(args, "-s", "--scenario", "pingpong");
        int taskCount = Integer.parseInt(readArg(args, "-t", "--tasks", "128"));
        int ranks = Integer.parseInt(readArg(args, "-r", "--ranks", "2"));
        int messageSizeBytes = Integer.parseInt(readArg(args, "-m", "--message-size", "1024"));

        List<BenchmarkResult> results = executeBenchmark(architectureValue, scenarioValue, taskCount, ranks, messageSizeBytes);
        recordLatestResults(results, taskCount, ranks, messageSizeBytes);
        Path outputPath = Path.of("experiments", "results", "benchmark-results.csv");
        MetricsExporter.exportCsv(outputPath, results);

        System.out.println(MpiRuntime.describe());
        System.out.println("Saved " + results.size() + " benchmark rows to " + outputPath.toAbsolutePath());
        for (BenchmarkResult result : results) {
            System.out.println(result.architecture() + "/" + result.benchmark() + ": durationMs=" + result.durationMs()
                    + ", throughput=" + String.format(java.util.Locale.US, "%.3f", result.throughput())
                    + ", cpuUtilization=" + String.format(java.util.Locale.US, "%.3f", result.cpuUtilization()));
        }
    }

    private static List<BenchmarkResult> executeBenchmark(String architectureValue, String scenarioValue, int taskCount, int ranks, int messageSizeBytes) {
        List<String> architectures = architectureValue.equalsIgnoreCase("all")
                ? List.of("A", "B", "C", "D")
                : List.of(architectureValue);
        List<BenchmarkScenario> scenarios = scenarioList(scenarioValue);

        List<BenchmarkResult> results = new ArrayList<>();
        for (String architectureId : architectures) {
            ExecutionArchitecture architecture = ExecutionArchitecture.fromValue(architectureId);
            ExperimentExecutor executor = createExecutor(architecture);
            for (BenchmarkScenario scenario : scenarios) {
                results.add(scenario.execute(executor, ranks, taskCount, messageSizeBytes));
            }
        }
        return results;
    }

    private static void recordLatestResults(List<BenchmarkResult> results, int taskCount, int ranks, int messageSizeBytes) {
        if (results.isEmpty()) {
            return;
        }

        BenchmarkResult first = results.get(0);
        LAST_DURATION_MS.set(first.durationMs());
        LAST_THROUGHPUT.set((long) first.throughput());
        LAST_ARCHITECTURE.set(first.architecture());
        LAST_SCENARIO.set(first.benchmark());
        LAST_META.clear();
        LAST_META.put("tasks", String.valueOf(taskCount));
        LAST_META.put("ranks", String.valueOf(ranks));
        LAST_META.put("message_size_bytes", String.valueOf(messageSizeBytes));
        LAST_META.put("results_count", String.valueOf(results.size()));
        LAST_RESULTS.set(List.copyOf(results));
    }

    private static List<BenchmarkScenario> scenarioList(String scenarioValue) {
        return switch (scenarioValue.toLowerCase()) {
            case "pingpong" -> List.of(new PingPongBenchmark());
            case "allreduce" -> List.of(new AllReduceBenchmark());
            case "montecarlo" -> List.of(new MonteCarloBenchmark());
            case "heat" -> List.of(new HeatEquationBenchmark());
            case "all" -> List.of(new PingPongBenchmark(), new AllReduceBenchmark(), new MonteCarloBenchmark(), new HeatEquationBenchmark());
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenarioValue);
        };
    }

    private static ExperimentExecutor createExecutor(ExecutionArchitecture architecture) {
        return switch (architecture) {
            case A -> new PureMpiExecutor();
            case B -> new PlatformThreadExecutor();
            case C -> new VirtualThreadExecutor();
            case D -> new DedicatedMpiExecutor();
        };
    }

    private static boolean hasHelpFlag(String[] args) {
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasServerFlag(String[] args) {
        for (String arg : args) {
            if ("--server".equals(arg) || "-server".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String readArg(String[] args, String shortName, String longName, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (shortName.equals(args[i]) || longName.equals(args[i])) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }
            }
        }
        return defaultValue;
    }

    private static String readArgFromQuery(HttpExchange exchange, String key, String defaultValue) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return defaultValue;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length >= 2 && key.equals(kv[0])) {
                try {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    return kv[1];
                }
            }
        }
        return defaultValue;
    }

    private static void sendText(HttpExchange exchange, int status, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar target/...jar [--server] [-a <A|B|C|D|all> -s <pingpong|allreduce|montecarlo|heat|all> -t <taskCount> -r <ranks> -m <messageBytes>]");
        System.out.println("Example: java -jar target/...jar --server");
        System.out.println("Example: java -jar target/...jar -a all -s pingpong -t 128 -r 2 -m 1024");
    }
}
