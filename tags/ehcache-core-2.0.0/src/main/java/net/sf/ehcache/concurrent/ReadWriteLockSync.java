/**
 *  Copyright 2003-2009 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.sf.ehcache.concurrent;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.TimeUnit;

/**
 * A simple ReadWriteLock synchronizer.
 *
 * @author Alex Snaps
 */
public class ReadWriteLockSync implements Sync, Comparable<ReadWriteLockSync> {

    private final ReentrantReadWriteLock rrwl = new ReentrantReadWriteLock();
    private final Lock readLock = rrwl.readLock();
    private final Lock writeLock = rrwl.writeLock();

    /**
     * {@inheritDoc}
     */
    public void lock(final LockType type) {
        if (type == LockType.WRITE && !rrwl.isWriteLockedByCurrentThread()) {
            getLock(type).lock();
        } else if (type != LockType.WRITE) {
            getLock(type).lock();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean tryLock(final LockType type, final long msec) throws InterruptedException {
        return getLock(type).tryLock(msec, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    public void unlock(final LockType type) {
        Lock lock = getLock(type);
        if (type == LockType.WRITE && rrwl.isWriteLockedByCurrentThread()) {
            lock.unlock();
        } else if (type != LockType.WRITE) {
            lock.unlock();
        }
    }

    private Lock getLock(final LockType type) {
        switch (type) {
            case READ:
                return readLock;
            case WRITE:
                return writeLock;
            default:
                throw new IllegalArgumentException("We don't support any other lock type than READ or WRITE!");
        }
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(ReadWriteLockSync o) {
        return  String.valueOf(hashCode()).compareTo(String.valueOf(o.hashCode()));    
    }
}
