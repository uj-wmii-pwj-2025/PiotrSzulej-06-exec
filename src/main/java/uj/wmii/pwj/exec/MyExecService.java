package uj.wmii.pwj.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {

    private BlockingQueue<Runnable> tasksQueue;
    private Thread thread;
    private boolean shutdown;
    private boolean terminated;
    private Object terminationLock = new Object();

    public MyExecService()
    {
        tasksQueue = new LinkedBlockingQueue<>();
        thread = new Thread(this::runWorker,"Worker");
        shutdown = false;
        terminated = false;
        thread.start();
    }

    private void runWorker()
    {
        try {
            while (true)
            {
                Runnable task = null;
                try {
                    task = tasksQueue.poll(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    if (shutdown)
                    {
                        break;
                    }
                    Thread.currentThread().interrupt();
                }

                if (task != null)
                {
                    try {
                        task.run();
                    } catch (Throwable t) {

                    }
                }
                else
                {
                    if (shutdown && tasksQueue.isEmpty())
                    {
                        break;
                    }
                }
            }
        } finally {
            synchronized (terminationLock){
                terminated = true;
                terminationLock.notifyAll();
            }
        }
    }

    static MyExecService newInstance() {
        return new MyExecService();
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        thread.interrupt();
        List<Runnable> remaining = new ArrayList<>();
        tasksQueue.drainTo(remaining);
        return remaining;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {

        long time = unit.toMillis(timeout);
        long end = System.currentTimeMillis() + time;

        synchronized (terminationLock) {
            while (!terminated)
            {
                long remain = end - System.currentTimeMillis();
                if (remain <= 0)
                {
                    break;
                }
                terminationLock.wait(remain);
            }
            return terminated;
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null)
        {
            throw new NullPointerException();
        }

        if (shutdown)
        {
            throw new RejectedExecutionException("shutdown");
        }

        FutureTask<T> ft = new FutureTask<>(task);
        execute(ft);
        return ft;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null)
        {
            throw new NullPointerException();
        }

        if (shutdown)
        {
            throw new RejectedExecutionException("shutdown");
        }

        FutureTask<T> ft = new FutureTask<>(task, result);
        execute(ft);
        return ft;
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (task == null)
        {
            throw new NullPointerException();
        }

        if (shutdown)
        {
            throw new RejectedExecutionException("shutdown");
        }

        FutureTask<?> ft = new FutureTask<>(task, null);
        execute(ft);
        return ft;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null)
        {
            throw new NullPointerException();
        }

        if (shutdown)
        {
            throw new RejectedExecutionException("shutdown");
        }

        List<Future<T>> futures = new ArrayList<>(tasks.size());

        for (Callable<T> c : tasks)
        {
            FutureTask<T> ft = new FutureTask<>(c);
            futures.add(ft);
            try {
                execute(ft);
            } catch (RejectedExecutionException r) {

                for (Future<T> f : futures)
                {
                    if (f != null) f.cancel(true);
                }
                throw r;
            }
        }

        for (Future<T> f : futures)
        {
            try {
                f.get();

            } catch (ExecutionException ee) {

            } catch (CancellationException ce) {

            }
        }

        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null || unit == null)
        {
            throw new NullPointerException();
        }

        long time = unit.toMillis(timeout);
        long end = System.currentTimeMillis() + time;

        List<Future<T>> futures = new ArrayList<>(tasks.size());

        try {
            for (Callable<T> c : tasks)
            {
                if (c == null)
                {
                    throw new NullPointerException();
                }
                FutureTask<T> ft = new FutureTask<>(c);
                futures.add(ft);
                execute(ft);
            }
        } catch (RuntimeException e) {

            for (Future<T> f : futures)
            {
                if (f != null) f.cancel(true);
            }
            throw e;
        }

        for (Future<T> f : futures)
        {
            if (f.isDone()) continue;

            long reamain = end - System.currentTimeMillis();
            if (reamain <= 0) {

                for (Future<T> fut : futures)
                {
                    if (!fut.isDone()) fut.cancel(true);
                }
                return futures;
            }

            try {
                f.get(reamain, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {

            } catch (TimeoutException t) {

                for (Future<T> fut : futures)
                {
                    if (!fut.isDone()) fut.cancel(true);
                }
                return futures;
            } catch (InterruptedException i) {

                for (Future<T> fut : futures)
                {
                    if (!fut.isDone()) fut.cancel(true);
                }
                throw i;
            }
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (tasks == null)
        {
            throw new NullPointerException();
        }

        List<Future<T>> futures = new ArrayList<>();
        ExecutionException lastException = null;

        try {

            for (Callable<T> task : tasks)
            {
                Future<T> f = submit(task);
                futures.add(f);
            }

            boolean finished = false;
            T result = null;

            while (!finished)
            {
                finished = true;
                for (Future<T> f : futures)
                {
                    if (!f.isDone())
                    {
                        finished = false;
                        continue;
                    }

                    try {
                        result = f.get();

                        for (Future<T> other : futures)
                        {
                            if (other != f)
                            {
                                other.cancel(true);
                            }
                        }
                        return result;
                    } catch (ExecutionException ex) {
                        lastException = ex;
                    } catch (CancellationException ce) {

                    }
                }

                if (!finished) {
                    Thread.sleep(10);
                }
            }
        } finally {

            for (Future<T> f : futures) f.cancel(true);
        }


        if (lastException != null)
        {
            throw lastException;
        }

        throw new ExecutionException(new Exception("No task completed successfully"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null || unit == null)
        {
            throw new NullPointerException();
        }

        long end = System.currentTimeMillis() + unit.toMillis(timeout);
        List<Future<T>> futures = new ArrayList<>();
        ExecutionException lastException = null;

        try {

            for (Callable<T> task : tasks)
            {
                Future<T> f = submit(task);
                futures.add(f);
            }

            boolean finished = false;

            while (!finished)
            {
                finished = true;
                long remain = end - System.currentTimeMillis();
                if (remain <= 0)
                {
                    throw new TimeoutException();
                }

                for (Future<T> f : futures)
                {
                    if (!f.isDone())
                    {
                        finished = false;
                        continue;
                    }

                    try {
                        T result = f.get();
                        for (Future<T> other : futures)
                        {
                            if (other != f)
                            {
                                other.cancel(true);
                            }
                        }
                        return result;
                    } catch (ExecutionException ex) {
                        lastException = ex;
                    } catch (CancellationException ce) {

                    }
                }

                if (!finished) {
                    Thread.sleep(Math.min(10, remain));
                }
            }

        } finally {
            for (Future<T> f : futures)
            {
                f.cancel(true);
            }
        }

        if (lastException != null)
        {
            throw lastException;
        }

        throw new ExecutionException(new Exception("No task completed successfully"));
    }

    @Override
    public void execute(Runnable command) {
        if (command == null)
        {
            throw new NullPointerException();
        }

        if (shutdown)
        {
            throw new RejectedExecutionException("shutdown");
        }

        tasksQueue.offer(command);
    }
}
