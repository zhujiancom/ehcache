/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.ehcache.tests.servermap;

import net.sf.ehcache.Cache;

import org.terracotta.api.ClusteringToolkit;
import org.terracotta.coordination.Barrier;
import org.terracotta.ehcache.tests.ClientBase;

public class ServerMapL2EvictionReachesOneL1Verifier extends ClientBase {

  public ServerMapL2EvictionReachesOneL1Verifier(String[] args) {
    super("test", args);
  }

  public static void main(String[] args) {
    new ServerMapL2EvictionReachesOneL1Verifier(args).run();
  }

  @Override
  protected void test(Cache cache, ClusteringToolkit clusteringToolkit) throws Throwable {
    System.out.println("in the verifier");

    EvictionCountingEventListener countingListener = new EvictionCountingEventListener(
                                                                                       clusteringToolkit
                                                                                           .getAtomicLong("EvictionCounter"));
    cache.getCacheEventNotificationService().registerListener(countingListener);

    Barrier barrier = clusteringToolkit.getBarrier("barrier", 2);
    barrier.await();
    long value = countingListener.getEvictedCount();
    System.out.println("After sleeping 2 mins: value=" + value);
    assertTrue("Expected at most " + ServerMapL2EvictionReachesOneL1TestClient.EXPECTED_EVICTION_COUNT
                   + " elements to have been evicted, value=" + value,
               (value <= ServerMapL2EvictionReachesOneL1TestClient.EXPECTED_EVICTION_COUNT));
  }
}