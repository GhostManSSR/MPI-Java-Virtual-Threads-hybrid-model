package research;

import org.junit.jupiter.api.Test;
import research.execution.DedicatedMpiExecutor;
import research.execution.PlatformThreadExecutor;
import research.execution.PureMpiExecutor;
import research.execution.VirtualThreadExecutor;
import research.metrics.BenchmarkResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkExecutionTest {
    @Test
    void runsAllExecutionArchitecturesWithoutCrashing() {
        BenchmarkResult pureMpi = new PureMpiExecutor().run("pingpong", 2, 32, 1024);
        List<BenchmarkResult> results = List.of(
                pureMpi,
                new PlatformThreadExecutor().run("pingpong", 2, 32, 1024),
                new VirtualThreadExecutor().run("pingpong", 2, 32, 1024),
                new DedicatedMpiExecutor().run("pingpong", 2, 32, 1024));

        for (BenchmarkResult result : results) {
            assertTrue(result.durationMs() > 0);
            assertTrue(result.cpuUtilization() >= 0);
            assertTrue(result.cpuUtilization() <= 1);
            assertTrue(result.queueWaitMs() >= 0);
            assertTrue(Boolean.TRUE.equals(result.extras().get("cpuUtilizationMeasured")));
            assertTrue(Boolean.TRUE.equals(result.extras().get("queueWaitMeasured")));
        }
        assertEquals(0d, pureMpi.queueWaitMs());
    }
}
