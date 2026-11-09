package research.metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MetricsExporter {
    private MetricsExporter() {
    }

    public static void exportCsv(Path target, List<BenchmarkResult> results) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(BenchmarkResult.csvHeader());
        for (BenchmarkResult result : results) {
            lines.add(result.toCsvRow());
        }
        Files.createDirectories(target.getParent());
        Files.write(target, lines, StandardCharsets.UTF_8);
    }
}
