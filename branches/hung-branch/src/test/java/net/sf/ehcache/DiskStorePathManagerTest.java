/**
 *  Copyright 2003-2010 Terracotta, Inc.
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

package net.sf.ehcache;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class DiskStorePathManagerTest {
    private DiskStorePathManager dspm1;
    private DiskStorePathManager dspm2;

    @Test
    public void testCollisionSameThread() throws Exception {
        String diskStorePath = getTempDir("testCollisionSameThread") + "/a/b/c";
        dspm1 = new DiskStorePathManager(diskStorePath);
        dspm2 = new DiskStorePathManager(diskStorePath);

        Assert.assertFalse(dspm1.getDiskStorePath().equals(dspm2.getDiskStorePath()));
    }

    @Test
    public void testCollisionDifferentThread() throws Exception {
        final String diskStorePath = getTempDir("testCollisionDifferentThread");
        dspm1 = new DiskStorePathManager(diskStorePath);
        Thread newThread = new Thread() {
            @Override
            public void run() {
                dspm2 = new DiskStorePathManager(diskStorePath);
            }
        };
        newThread.start();
        newThread.join(10 * 1000L);

        Assert.assertFalse(dspm1.getDiskStorePath().equals(dspm2.getDiskStorePath()));
    }

    @Test(expected=CacheException.class)
    public void testIllegalPath() {
        String diskStorePath = getTempDir("testCollisionDifferentThread") + "/?";
        dspm1 = new DiskStorePathManager(diskStorePath);
    }

    private String getTempDir(String dirname) {
        String base = System.getProperty("basedir") != null ? System.getProperty("basedir") : ".";
        File target = new File(base, "target");
        File tempBase = new File(target, DiskStorePathManagerTest.class.getSimpleName());
        File tempDir = new File(tempBase, dirname);
        tempDir.mkdirs();
        Assert.assertTrue(tempDir.isDirectory());
        return tempDir.getAbsolutePath();
    }

    @After
    public void tearDown() {
        if (dspm1 != null) {
            dspm1.releaseLock();
        }
        if (dspm2 != null) {
            dspm2.releaseLock();
        }
    }
}
