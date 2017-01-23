package com.github.salvatorenovelli.utils.concurrency;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LeftRightLockTest {


    private int logLineSequenceNumber = 0;
    private LeftRightLock sut = new LeftRightLock();

    @Test(timeout = 2000)
    public void acquiringLeftLockExcludeAcquiringRightLock() throws Exception {
        sut.lockLeft();


        Future<Boolean> task = Executors.newSingleThreadExecutor().submit(() -> sut.tryLockRight());
        assertFalse("I shouldn't be able to acquire the RIGHT lock!", task.get());
    }

    @Test(timeout = 2000)
    public void acquiringRightLockExcludeAcquiringLeftLock() throws Exception {
        sut.lockRight();
        Future<Boolean> task = Executors.newSingleThreadExecutor().submit(() -> sut.tryLockLeft());
        assertFalse("I shouldn't be able to acquire the LEFT lock!", task.get());
    }

    @Test(timeout = 2000)
    public void theLockShouldBeReentrant() throws Exception {
        sut.lockLeft();
        assertTrue(sut.tryLockLeft());
    }

    @Test(timeout = 2000)
    public void multipleThreadShouldBeAbleToAcquireTheSameLock_Right() throws Exception {
        sut.lockRight();
        Future<Boolean> task = Executors.newSingleThreadExecutor().submit(() -> sut.tryLockRight());
        assertTrue(task.get());
    }

    @Test(timeout = 2000)
    public void multipleThreadShouldBeAbleToAcquireTheSameLock_left() throws Exception {
        sut.lockLeft();
        Future<Boolean> task = Executors.newSingleThreadExecutor().submit(() -> sut.tryLockLeft());
        assertTrue(task.get());
    }

    @Test(timeout = 2000)
    public void shouldKeepCountOfAllTheThreadsHoldingTheSide() throws Exception {

        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);

        Thread threadA = spawnThreadToAcquireLeftLock(latchA, sut);
        Thread threadB = spawnThreadToAcquireLeftLock(latchB, sut);

        System.out.println("Both threads have acquired the left lock.");

        try {
            latchA.countDown();
            threadA.join();
            boolean acqStatus = sut.tryLockRight();
            assertFalse("There is still a thread holding the left lock. This shouldn't succeed.", acqStatus);
        } finally {
            latchB.countDown();
            threadB.join();
        }

    }

    @Test(timeout = 5000)
    public void shouldBlockThreadsTryingToAcquireLeftIfRightIsHeld() throws Exception {
        sut.lockLeft();

        CountDownLatch taskStartedLatch = new CountDownLatch(1);

        final Future<Boolean> task = Executors.newSingleThreadExecutor().submit(() -> {
            taskStartedLatch.countDown();
            sut.lockRight();
            return false;
        });

        taskStartedLatch.await();
        Thread.sleep(100);

        assertFalse(task.isDone());
    }

    @Test
    public void shouldBeFreeAfterRelease() throws Exception {
        sut.lockLeft();
        sut.releaseLeft();
        assertTrue(sut.tryLockRight());
    }

    @Test
    public void shouldBeFreeAfterMultipleThreadsReleaseIt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        final Thread thread1 = spawnThreadToAcquireLeftLock(latch, sut);
        final Thread thread2 = spawnThreadToAcquireLeftLock(latch, sut);

        latch.countDown();

        thread1.join();
        thread2.join();

        assertTrue(sut.tryLockRight());

    }

    @Test(timeout = 2000)
    public void lockShouldBeReleasedIfNoThreadIsHoldingIt() throws Exception {
        CountDownLatch releaseLeftLatch = new CountDownLatch(1);
        CountDownLatch rightLockTaskIsRunning = new CountDownLatch(1);

        Thread leftLockThread1 = spawnThreadToAcquireLeftLock(releaseLeftLatch, sut);
        Thread leftLockThread2 = spawnThreadToAcquireLeftLock(releaseLeftLatch, sut);

        Future<Boolean> acquireRightLockTask = Executors.newSingleThreadExecutor().submit(() -> {
            if (sut.tryLockRight())
                throw new AssertionError("The left lock should be still held, I shouldn't be able to acquire right a this point.");
            printSynchronously("Going to be blocked on right lock");
            rightLockTaskIsRunning.countDown();
            sut.lockRight();
            printSynchronously("Lock acquired!");
            return true;
        });

        rightLockTaskIsRunning.await();

        releaseLeftLatch.countDown();
        leftLockThread1.join();
        leftLockThread2.join();

        assertTrue(acquireRightLockTask.get());
    }

    private synchronized void printSynchronously(String str) {

        System.out.println(logLineSequenceNumber++ + ")" + str);
        System.out.flush();
    }

    private Thread spawnThreadToAcquireLeftLock(CountDownLatch releaseLockLatch, LeftRightLock lock) throws InterruptedException {
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        final Thread thread = spawnThreadToAcquireLeftLock(releaseLockLatch, lockAcquiredLatch, lock);
        lockAcquiredLatch.await();
        return thread;
    }

    private Thread spawnThreadToAcquireLeftLock(CountDownLatch releaseLockLatch, CountDownLatch lockAcquiredLatch, LeftRightLock lock) {
        final Thread thread = new Thread(() -> {
            lock.lockLeft();
            printSynchronously("Thread " + Thread.currentThread() + " Acquired left lock");
            try {
                lockAcquiredLatch.countDown();
                releaseLockLatch.await();
            } catch (InterruptedException ignore) {
            } finally {
                lock.releaseLeft();
            }

            printSynchronously("Thread " + Thread.currentThread() + " RELEASED left lock");
        });
        thread.start();
        return thread;
    }
}
