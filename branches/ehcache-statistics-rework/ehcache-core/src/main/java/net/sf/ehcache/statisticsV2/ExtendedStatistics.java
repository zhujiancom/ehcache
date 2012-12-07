/**
 *  Copyright Terracotta, Inc.
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

package net.sf.ehcache.statisticsV2;

public interface ExtendedStatistics {

    public abstract float getAverageGetTime();

    public abstract long getCacheHitMostRecentSample();

    public abstract long getCacheMissMostRecentSample();

    public abstract long getPutCount();

    public abstract long getCacheElementPutMostRecentSample();

    public abstract int getCacheMissNotFoundMostRecentSample();

    public abstract int getCacheMissExpiredMostRecentSample();

    public abstract long getMaxGetTimeMillis();

    public abstract long getMinGetTimeMillis();

    public abstract long getCacheHitInMemoryMostRecentSample();

    public abstract long getCacheHitOffHeapMostRecentSample();

    public abstract long getCacheHitOnDiskMostRecentSample();

    public abstract long getCacheMissInMemoryMostRecentSample();

    public abstract long getCacheMissOffHeapMostRecentSample();

    public abstract long getCacheMissOnDiskMostRecentSample();

    public abstract long getCacheElementUpdatedMostRecentSample();

    public abstract long getCacheElementRemovedMostRecentSample();

    public abstract long getCacheElementEvictedMostRecentSample();

    public abstract long getCacheElementExpiredMostRecentSample();

    public abstract long getSearchesPerSecond();

    public abstract long getAverageSearchTime();

    public abstract long getCacheXaCommitsMostRecentSample();

    public abstract long getCacheXaRollbacksMostRecentSample();

}