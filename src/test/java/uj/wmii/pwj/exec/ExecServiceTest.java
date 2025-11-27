package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(10);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertThrows(
            RejectedExecutionException.class,
            () -> s.submit(new TestRunnable()));
    }

    @Test
    void testIsShutdownAndIsTerminated() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();
        assertFalse(s.isShutdown());
        assertFalse(s.isTerminated());

        s.execute(new TestRunnable());
        s.shutdown();

        assertTrue(s.isShutdown());

        assertTrue(s.awaitTermination(1, TimeUnit.SECONDS));
        assertTrue(s.isTerminated());
    }

    @Test
    void testAwaitTermination() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();

        Runnable longTask = new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        s.execute(longTask);
        s.shutdown();

        boolean terminated = s.awaitTermination(2, TimeUnit.SECONDS);

        assertTrue(terminated);
        assertTrue(s.isTerminated());
    }

    @Test
    void testShutdownNow() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();

        TestRunnable r1 = new TestRunnable();
        TestRunnable r2 = new TestRunnable();

        s.execute(r1);
        s.execute(r2);

        List<Runnable> notExecuted = s.shutdownNow();

        assertTrue(s.isShutdown());

        for (Runnable r : notExecuted)
        {
            assertTrue(r == r1 || r == r2);
        }
    }

    @Test
    void testSubmitCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        Callable<String> c = () -> "hello";

        Future<String> future = s.submit(c);
        String result = future.get(1, TimeUnit.SECONDS);

        assertEquals("hello", result);
        assertTrue(future.isDone());
    }

    @Test
    void testSubmitRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        Runnable r = () -> System.out.println("Task executed");

        Future<String> future = s.submit(r, "ok");
        String result = future.get(1, TimeUnit.SECONDS);

        assertEquals("ok", result);
        assertTrue(future.isDone());
    }

    @Test
    void testSubmitRunnable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        Runnable r = () -> System.out.println("Task executed");

        Future<?> future = s.submit(r);
        future.get(1, TimeUnit.SECONDS);

        assertTrue(future.isDone());
    }

    @Test
    void testInvokeAll() throws InterruptedException, ExecutionException {
        MyExecService s = MyExecService.newInstance();

        Collection<Callable<String>> tasks = new ArrayList<>();
        tasks.add(new StringCallable("A", 5));
        tasks.add(new StringCallable("B", 5));

        List<Future<String>> results = s.invokeAll(tasks);

        for (Future<String> f : results)
        {
            assertTrue(f.isDone());
            assertTrue(f.get().equals("A") || f.get().equals("B"));
        }
    }

    @Test
    void testInvokeAllWithTimeout() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();

        Collection<Callable<String>> tasks = new ArrayList<>();
        tasks.add(new StringCallable("A", 50));
        tasks.add(new StringCallable("B", 50));

        List<Future<String>> results = s.invokeAll(tasks, 10, TimeUnit.MILLISECONDS);

        boolean anyCancelled = false;
        for (Future<String> f : results)
        {
            if (f.isCancelled())
            {
                anyCancelled = true;
            }
        }
        assertTrue(anyCancelled);
    }

    @Test
    void testInvokeAny() throws InterruptedException, ExecutionException {
        MyExecService s = MyExecService.newInstance();

        Collection<Callable<String>> tasks = new ArrayList<>();
        tasks.add(new StringCallable("A", 5));
        tasks.add(new StringCallable("B", 5));

        String result = s.invokeAny(tasks);

        assertTrue(result.equals("A") || result.equals("B"));
    }

    @Test
    void testInvokeAnyWithTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        MyExecService s = MyExecService.newInstance();

        Collection<Callable<String>> tasks = new ArrayList<>();
        tasks.add(new StringCallable("A", 100));
        tasks.add(new StringCallable("B", 100));

        String result = s.invokeAny(tasks, 1, TimeUnit.SECONDS);

        assertTrue(result.equals("A") || result.equals("B"));
    }

    @Test
    void testRejectedExecutionAfterShutdown() {
        MyExecService s = MyExecService.newInstance();
        s.shutdown();

        assertThrows(RejectedExecutionException.class, () -> s.execute(() -> {}));
        assertThrows(RejectedExecutionException.class, () -> s.submit(() -> "X"));
        assertThrows(RejectedExecutionException.class, () -> s.submit(() -> "Y"));
        assertThrows(RejectedExecutionException.class, () -> s.submit(() -> {}, "Z"));
    }

    @Test
    void testNullTask() {
        MyExecService s = MyExecService.newInstance();

        assertThrows(NullPointerException.class, () -> s.submit((Callable<String>) null));
        assertThrows(NullPointerException.class, () -> s.submit((Runnable) null));
        assertThrows(NullPointerException.class, () -> s.submit((Runnable) null, "X"));
    }

    @Test
    void testInvokeAllWithNull() {
        MyExecService s = MyExecService.newInstance();

        assertThrows(NullPointerException.class, () -> s.invokeAll(null));
        assertThrows(NullPointerException.class, () -> s.invokeAll(null, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> s.invokeAll(new java.util.ArrayList<Callable<String>>(), 1, null));
    }

    @Test
    void testInvokeAnyWithNull() {
        MyExecService s = MyExecService.newInstance();

        assertThrows(NullPointerException.class, () -> s.invokeAny(null));
        assertThrows(NullPointerException.class, () -> s.invokeAny(null, 1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> s.invokeAny(new java.util.ArrayList<Callable<String>>(), 1, null));
    }

    @Test
    void testTimeoutExceptionInInvokeAny() {
        MyExecService s = MyExecService.newInstance();

        List<Callable<String>> tasks = new ArrayList<>();
        tasks.add(() -> { Thread.sleep(200); return "A"; });
        tasks.add(() -> { Thread.sleep(200); return "B"; });

        assertThrows(TimeoutException.class, () -> s.invokeAny(tasks, 50, TimeUnit.MILLISECONDS));
    }

    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}

class StringCallable implements Callable<String> {

    private final String result;
    private final int milis;

    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }

    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}
class TestRunnable implements Runnable {

    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }
}
