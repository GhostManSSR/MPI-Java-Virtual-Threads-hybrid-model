package research;

import org.junit.jupiter.api.Test;
import research.execution.ExecutionArchitecture;
import research.mpi.MpiRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MpiRuntimeTest {
    @Test
    void detectsRuntimeThreadSupport() {
        assertNotNull(MpiRuntime.detectProvidedLevel());
        assertNotNull(MpiRuntime.describe());
    }

    @Test
    void requiresThreadSupportForEachArchitecture() {
        assertEquals(
                MpiRuntime.ThreadSupportLevel.SINGLE,
                MpiRuntime.requiredThreadLevel(ExecutionArchitecture.A));
        assertEquals(
                MpiRuntime.ThreadSupportLevel.FUNNELED,
                MpiRuntime.requiredThreadLevel(ExecutionArchitecture.B));
        assertEquals(
                MpiRuntime.ThreadSupportLevel.FUNNELED,
                MpiRuntime.requiredThreadLevel(ExecutionArchitecture.C));
        assertEquals(
                MpiRuntime.ThreadSupportLevel.MULTIPLE,
                MpiRuntime.requiredThreadLevel(ExecutionArchitecture.D));
    }
}
