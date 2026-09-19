package ru.d2omg.d2save;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One bounded writer; a barrier waits for all earlier writes, including failed ones. */
public final class SaveQueue implements AutoCloseable {
    private final ExecutorService executor;
    private final Semaphore slots;
    private final Consumer<Throwable> onFailure;
    private volatile boolean failed;
    private volatile boolean closed;
    private final AtomicLong completed = new AtomicLong();

    public SaveQueue(int capacity, Consumer<Throwable> onFailure) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.slots = new Semaphore(capacity);
        this.onFailure = onFailure;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "D2Save writer");
            thread.setDaemon(false);
            return thread;
        });
    }

    public synchronized boolean submit(Runnable task) {
        if (closed || failed) return false;
        slots.acquireUninterruptibly();
        try {
            executor.execute(() -> {
                try { task.run(); completed.incrementAndGet(); }
                catch (Throwable error) { failed = true; onFailure.accept(error); }
                finally { slots.release(); }
            });
        } catch (RuntimeException error) { slots.release(); throw error; }
        return true;
    }

    public synchronized void drain() {
        if (closed) return;
        Future<?> barrier = executor.submit(() -> {});
        boolean interrupted = false;
        try {
            while (true) {
                try { barrier.get(); break; }
                catch (InterruptedException error) { interrupted = true; }
                catch (ExecutionException error) { throw new IllegalStateException(error.getCause()); }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    public long completed() { return completed.get(); }
    public boolean failed() { return failed; }

    @Override public synchronized void close() {
        if (closed) return;
        drain();
        closed = true;
        executor.shutdown();
    }
}
