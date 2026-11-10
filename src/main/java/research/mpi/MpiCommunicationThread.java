package research.mpi;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class MpiCommunicationThread implements AutoCloseable {
    private final BlockingQueue<FutureTask<?>> queue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private volatile boolean running = true;

    public MpiCommunicationThread() {
        thread = new Thread(this::runLoop, "mpi-communication-thread");
        thread.start();
    }

    public <T> T submit(Callable<T> operation) {
        if (!running) {
            throw new IllegalStateException("MPI communication thread is closed");
        }
        FutureTask<T> task = new FutureTask<>(operation);
        queue.add(task);
        try {
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for MPI communication", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("MPI communication failed", cause);
        }
    }

    private void runLoop() {
        while (running || !queue.isEmpty()) {
            try {
                FutureTask<?> task = queue.poll(10, TimeUnit.MILLISECONDS);
                if (task != null) {
                    task.run();
                }
            } catch (InterruptedException ex) {
                if (!running) {
                    break;
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping MPI communication thread", ex);
        }
    }
}
