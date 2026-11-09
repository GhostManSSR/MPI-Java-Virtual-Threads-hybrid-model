package research;

import org.junit.jupiter.api.Test;
import research.mpi.MpiRuntime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MpiRuntimeTest {
    @Test
    void detectsRuntimeThreadSupport() {
        assertNotNull(MpiRuntime.detectProvidedLevel());
        assertNotNull(MpiRuntime.describe());
    }
}
