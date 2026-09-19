package ru.d2omg.d2save;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaveQueueTest {
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }

    @Test void writesRunOffCallerAndInOrder() {
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> writer = new AtomicReference<>();
        try (SaveQueue q = new SaveQueue(8, e -> fail(e))) {
            for (int i = 0; i < 80; i++) {
                int value = i;
                assertTrue(q.submit(() -> { writer.set(Thread.currentThread()); order.add(value); }));
            }
            q.drain();
            assertNotSame(caller, writer.get());
            assertEquals(java.util.stream.IntStream.range(0,80).boxed().toList(), order);
            assertEquals(80, q.completed());
        }
    }

    @Test void enqueueDoesNotWaitForSlowDiskButDrainDoes() {
        CountDownLatch gate = new CountDownLatch(1), entered = new CountDownLatch(1);
        try (SaveQueue q = new SaveQueue(2, e -> fail(e))) {
            assertTimeout(Duration.ofSeconds(1), () -> q.submit(() -> { entered.countDown(); await(gate); }));
            await(entered);
            var future = CompletableFuture.runAsync(q::drain);
            assertFalse(future.isDone());
            gate.countDown();
            assertTimeoutPreemptively(Duration.ofSeconds(2), future::join);
        } finally { gate.countDown(); }
    }

    @Test void boundedQueueAppliesBackpressure() {
        CountDownLatch gate = new CountDownLatch(1);
        try (SaveQueue q = new SaveQueue(1, e -> fail(e))) {
            q.submit(() -> await(gate));
            var second = CompletableFuture.supplyAsync(() -> q.submit(() -> {}));
            assertFalse(second.isDone());
            gate.countDown();
            assertTrue(second.join());
            q.drain();
            assertEquals(2, q.completed());
        } finally { gate.countDown(); }
    }

    @Test void failureIsReportedAndDisablesFutureAsyncWrites() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (SaveQueue q = new SaveQueue(2, error::set)) {
            q.submit(() -> { throw new IllegalStateException("disk unavailable"); });
            q.drain();
            assertTrue(q.failed());
            assertEquals("disk unavailable", error.get().getMessage());
            assertFalse(q.submit(() -> fail("must use synchronous fallback")));
        }
    }

    @Test void closeDrainsAndRejectsNewWork() {
        AtomicInteger count = new AtomicInteger();
        SaveQueue q = new SaveQueue(4, e -> fail(e));
        q.submit(count::incrementAndGet);
        q.close();
        assertEquals(1, count.get());
        assertFalse(q.submit(count::incrementAndGet));
        q.close();
    }

    @Test void drainPreservesInterruptButStillCompletesWrites() {
        try (SaveQueue q = new SaveQueue(2, e -> fail(e))) {
            q.submit(() -> {});
            Thread.currentThread().interrupt();
            q.drain();
            assertTrue(Thread.interrupted());
            assertEquals(1, q.completed());
        } finally { Thread.interrupted(); }
    }

    @Test void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new SaveQueue(0, e -> {}));
    }
}
