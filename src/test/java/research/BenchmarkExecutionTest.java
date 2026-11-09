package research;

import org.junit.jupiter.api.Test;
import research.execution.DedicatedMpiExecutor;
import research.execution.PlatformThreadExecutor;
import research.execution.PureMpiExecutor;
import research.execution.VirtualThreadExecutor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkExecutionTest {
    @Test
    void runsAllExecutionArchitecturesWithoutCrashing() {
        assertTrue(new PureMpiExecutor().run("pingpong", 2, 32, 1024).durationMs() >= 0);
        assertTrue(new PlatformThreadExecutor().run("pingpong", 2, 32, 1024).durationMs() >= 0);
        assertTrue(new VirtualThreadExecutor().run("pingpong", 2, 32, 1024).durationMs() >= 0);
        assertTrue(new DedicatedMpiExecutor().run("pingpong", 2, 32, 1024).durationMs() >= 0);
    }
}
