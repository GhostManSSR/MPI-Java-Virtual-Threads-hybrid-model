package research.execution;

import java.lang.reflect.Method;

public final class ExecutorSupport {
    private ExecutorSupport() {
    }

    public static Thread startVirtualThread(Runnable runnable) {
        try {
            Method method = Thread.class.getMethod("startVirtualThread", Runnable.class);
            return (Thread) method.invoke(null, runnable);
        } catch (ReflectiveOperationException ignored) {
            Thread thread = new Thread(runnable);
            thread.start();
            return thread;
        }
    }
}
