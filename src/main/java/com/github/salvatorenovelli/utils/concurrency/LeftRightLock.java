package com.github.salvatorenovelli.utils.concurrency;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Lock;

/**
 * A binary mutex with the following properties:
 *
 * Exposes two different {@link Lock}s: LEFT, RIGHT.
 *
 * When LEFT is held other threads can acquire LEFT but thread trying to acquire RIGHT will be
 * blocked. When RIGHT is held other threads can acquire RIGHT but thread trying to acquire LEFT
 * will be blocked.
 */
public class LeftRightLock {

    private static final int ACQUISITION_FAILED = -1;
    private static final int ACQUISITION_SUCCEEDED = 1;

    private final LeftRightSync sync = new LeftRightSync();

    public void lockLeft() {
        sync.acquireShared(LockSide.LEFT.getV());
    }

    public void lockRight() {
        sync.acquireShared(LockSide.RIGHT.getV());
    }

    public void releaseLeft() {
        sync.releaseShared(LockSide.LEFT.getV());
    }

    public void releaseRight() {
        sync.releaseShared(LockSide.RIGHT.getV());
    }

    public boolean tryLockLeft() {
        return sync.tryAcquireShared(LockSide.LEFT) == ACQUISITION_SUCCEEDED;
    }

    public boolean tryLockRight() {
        return sync.tryAcquireShared(LockSide.RIGHT) == ACQUISITION_SUCCEEDED;
    }

    private enum LockSide {
        LEFT(-1), NONE(0), RIGHT(1);

        private final int v;

        LockSide(int v) {
            this.v = v;
        }

        public int getV() {
            return v;
        }
    }

    /**
     * <p> Keep count the count of threads holding either the LEFT or the RIGHT lock. </p>
     *
     * <li>A state ({@link AbstractQueuedSynchronizer#getState()}) greater than 0 means one or more
     * threads are holding RIGHT lock. </li> <li>A state ({@link AbstractQueuedSynchronizer#getState()})
     * lower than 0 means one or more threads are holding LEFT lock.</li> <li>A state ({@link
     * AbstractQueuedSynchronizer#getState()}) equal to zero means no thread is holding any
     * lock.</li>
     */
    private static final class LeftRightSync extends AbstractQueuedSynchronizer {


        @Override
        protected int tryAcquireShared(int requiredSide) {
            return (tryChangeThreadCountHoldingCurrentLock(requiredSide, ChangeType.ADD) ? ACQUISITION_SUCCEEDED : ACQUISITION_FAILED);
        }

        public boolean tryChangeThreadCountHoldingCurrentLock(int requiredSide, ChangeType changeType) {
            if (requiredSide != 1 && requiredSide != -1)
                throw new AssertionError("You can either lock LEFT or RIGHT (-1 or +1)");

            int curState;
            int newState;
            do {
                curState = this.getState();
                if (!sameSide(curState, requiredSide)) {
                    return false;
                }

                if (changeType == ChangeType.ADD) {
                    newState = curState + requiredSide;
                } else {
                    newState = curState - requiredSide;
                }
                //TODO: protect against int overflow (hopefully you won't have so many threads)
            } while (!this.compareAndSetState(curState, newState));
            return true;
        }

        @Override
        protected boolean tryReleaseShared(int requiredSide) {
            return tryChangeThreadCountHoldingCurrentLock(requiredSide, ChangeType.REMOVE);
        }

        final int tryAcquireShared(LockSide lockSide) {
            return this.tryAcquireShared(lockSide.getV());
        }

        final boolean tryReleaseShared(LockSide lockSide) {
            return this.tryReleaseShared(lockSide.getV());
        }

        private boolean sameSide(int curState, int requiredSide) {
            return curState == 0 || sameSign(curState, requiredSide);
        }

        private boolean sameSign(int a, int b) {
            return (a >= 0) ^ (b < 0);
        }

        public enum ChangeType {
            ADD, REMOVE
        }
    }
}
