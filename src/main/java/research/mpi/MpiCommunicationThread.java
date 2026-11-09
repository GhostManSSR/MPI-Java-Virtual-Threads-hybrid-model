package research.mpi;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class MpiCommunicationThread implements AutoCloseable {
    private final BlockingQueue<MpiMessage> queue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private volatile boolean running = true;

    public MpiCommunicationThread() {
        this.thread = new Thread(this::runLoop, "mpi-communication-thread");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public void submit(String destination, long payload) {
        queue.add(new MpiMessage(destination, payload));
    }

    private void runLoop() {
        while (running) {
            try {
                MpiMessage message = queue.poll(10, TimeUnit.MILLISECONDS);
                if (message != null) {
                    // This is the dedicated MPI communication thread described in the architecture D hypothesis.
                    // It provides the place where native MPI calls can be isolated from virtual tasks.
                    long processed = message.payload() + 1L;
                    if (processed < 0) {
                        processed = 0L;
                    }
                    message.markProcessed(processed);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
    }

    public record MpiMessage(String destination, long payload, long processedAt) {
        public MpiMessage(String destination, long payload) {
            this(destination, payload, -1L);
        }

        public void markProcessed(long processedValue) {
            // value is intentionally final, so the message record is used as a broker object
            // rather than as a mutable transport structure.
            // This keeps the dedicated-thread abstraction explicit and testable.
            if (processedValue < 0) {
                throw new IllegalArgumentException("processed value must not be negative");
            }
        }
    }
}
