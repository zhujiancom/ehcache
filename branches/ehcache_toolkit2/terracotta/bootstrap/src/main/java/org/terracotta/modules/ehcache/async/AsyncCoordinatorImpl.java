/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.modules.ehcache.ToolkitInstanceFactory;
import org.terracotta.modules.ehcache.ToolkitInstanceFactoryImpl;
import org.terracotta.modules.ehcache.async.errorhandlers.LoggingErrorHandler;
import org.terracotta.modules.ehcache.async.exceptions.ExistingRunningThreadException;
import org.terracotta.modules.ehcache.async.scatterpolicies.HashCodeScatterPolicy;
import org.terracotta.modules.ehcache.async.scatterpolicies.ItemScatterPolicy;
import org.terracotta.modules.ehcache.async.scatterpolicies.SingleBucketScatterPolicy;
import org.terracotta.toolkit.Toolkit;
import org.terracotta.toolkit.cluster.ClusterEvent;
import org.terracotta.toolkit.cluster.ClusterInfo;
import org.terracotta.toolkit.cluster.ClusterListener;
import org.terracotta.toolkit.cluster.ClusterNode;
import org.terracotta.toolkit.collections.ToolkitList;
import org.terracotta.toolkit.collections.ToolkitMap;
import org.terracotta.toolkit.concurrent.locks.ToolkitLock;
import org.terracotta.toolkit.concurrent.locks.ToolkitLockType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * An AsyncCoordinator allows work to be added and processed asynchronously in a fault-tolerant and high performance
 * fashion.
 */

public class AsyncCoordinatorImpl<E extends Serializable> implements AsyncCoordinator<E> {
  private static final Logger                          LOGGER    = LoggerFactory.getLogger(AsyncCoordinatorImpl.class
                                                                     .getName());
  private static final String                          BUCKET    = "bucket";
  private static final String                          DELIMITER = ToolkitInstanceFactoryImpl.DELIMITER;
  private final String                                 name;
  private final AsyncConfig                            config;

  /**
   * this ToolkitMap map contains keys based on asyncName-nodeId and value will be linked list of bucketNames (or name
   * of ToolkitList)
   */
  private final ToolkitMap<String, LinkedList<String>> listNamesMap;

  /**
   * lock for this coordinator based on SynchronousWrite
   */
  private final ToolkitLock                            coordinatorLock;
  private final List<ProcessingBucket<E>>              localBuckets;

  /**
   * status of this coordinator like STARTED, STOPPED etc
   */

  private volatile Status                              status;
  private ItemScatterPolicy<? super E>                 scatterPolicy;
  private ItemsFilter<E>                               filter;
  private final ClusterInfo                            cluster;
  private final String                                 nameListKey;
  private final Toolkit                                toolkit;
  private final ToolkitInstanceFactory                 toolkitInstanceFactory;
  private ItemProcessor<E>                             processor;
  private AsyncClusterListener                         listner;

  public AsyncCoordinatorImpl(String name, String nameListKey, AsyncConfig config,
                              ToolkitInstanceFactory toolkitInstanceFactory) {
    this.name = name;
    this.nameListKey = nameListKey;
    if (null == config) {
      this.config = DefaultAsyncConfig.getInstance();
    } else {
      this.config = config;
    }
    this.localBuckets = new ArrayList<ProcessingBucket<E>>();
    this.toolkitInstanceFactory = toolkitInstanceFactory;
    this.toolkit = toolkitInstanceFactory.getToolkit();
    this.listNamesMap = toolkitInstanceFactory.getOrCreateAsyncListNamesMap();
    ToolkitLockType lockType = config.isSynchronousWrite() ? ToolkitLockType.SYNCHRONOUS_WRITE : ToolkitLockType.WRITE;
    this.coordinatorLock = toolkit.getLock(nameListKey, lockType);
    this.cluster = toolkit.getClusterInfo();
  }

  @Override
  public void start(ItemProcessor<E> itemProcessor, int processingConcurrency, ItemScatterPolicy<? super E> policy) {
    if (null == processor) throw new IllegalArgumentException("processor can't be null");
    if (processingConcurrency < 1) throw new IllegalArgumentException("processingConcurrency needs to be at least 1");

    if (null == policy) {
      if (1 == processingConcurrency) {
        policy = new SingleBucketScatterPolicy();
      } else {
        policy = new HashCodeScatterPolicy();
      }
    }

    Lock lock = coordinatorLock;
    lock.lock();
    try {
      if (status == Status.STARTED) {
        LOGGER.warn("AsyncCoordinator " + name + " already started");
        return;
      }
      if (scatterPolicy != null) { throw new IllegalArgumentException(
                                                                      "scatterPolicy should have been null for AsyncCoordinator "
                                                                          + name); }
      this.scatterPolicy = policy;
      LinkedList<String> nameList = listNamesMap.get(nameListKey);
      if (!nameList.isEmpty()) { throw new IllegalArgumentException("nameList already populated for AsyncCoordinator "
                                                                    + name); }
      this.processor = itemProcessor;
      for (int i = 0; i < processingConcurrency; i++) {
        String bucketName = nameListKey + DELIMITER + BUCKET + DELIMITER + i;
        ToolkitList<E> toolkitList = toolkit.getList(bucketName);
        ProcessingBucket<E> bucket = new ProcessingBucket<E>(bucketName, config, toolkitList, cluster, processor,
                                                             LoggingErrorHandler.getInstance());
        bucket.setItemsFilter(filter);
        localBuckets.add(bucket);
        nameList.add(bucketName);
      }
      listNamesMap.put(nameListKey, nameList);
      listner = new AsyncClusterListener();
      cluster.addClusterListener(listner);
      status = Status.STARTED;
      for (ProcessingBucket<E> bucket : localBuckets) {
        try {
          bucket.start(true);
        } catch (ExistingRunningThreadException e) {
          stop();
          throw new IllegalStateException(bucket.getBucketName() + " already started for AsyncCoordinator " + name);
        }
      }
    } finally {
      lock.unlock();
    }

    // checking if there are any dead nodes and starting threads for those buckets also
    Set<String> deadNodes = determineDeadNodes();
    for (String otherNodeNameListKey : deadNodes) {
      processOtherNode(otherNodeNameListKey);
    }

  }

  @Override
  public void add(E item) {
    if (null == item) { return; }
    Lock lock = coordinatorLock;
    lock.lock();
    try {
      getStatus().checkRunning();
      final int index = scatterPolicy.selectBucket(localBuckets.size(), item);
      final ProcessingBucket bucket = localBuckets.get(index);
      bucket.add(item);
    } finally {
      lock.unlock();
    }
  }

  private Status getStatus() {
    return status != null ? status : Status.UNINITIALIZED;
  }

  @Override
  public void stop() {
    Lock lock = coordinatorLock;
    lock.lock();
    try {
      if (status == Status.STARTED) {
        for (ProcessingBucket<E> bucket : localBuckets) {
          bucket.stop();
        }
        localBuckets.clear();
        scatterPolicy = null;
        if (cluster != null) {
          cluster.removeClusterListener(listner);
        }
        LinkedList<String> nameList = listNamesMap.get(nameListKey);
        nameList.clear();
        listNamesMap.put(nameListKey, nameList);
        status = Status.STOPPED;
      }
    } finally {
      lock.unlock();
    }

  }

  /**
   * Attach the specified {@code QuarantinedItemsFilter} to this coordinator.
   * <p>
   * A quarantined items filter allows scheduled work to be filtered (and possibly skipped) before being executed.
   * <p>
   * Assigning {@code null} as the quarantined filter causes any existing filter to be removed.
   * 
   * @param filter filter to be applied
   */
  public void setOperationsFilter(ItemsFilter<E> filter) {
    Lock lock = coordinatorLock;
    lock.lock();
    try {
      this.filter = filter;
      if (localBuckets != null) {
        for (ProcessingBucket<E> bucket : localBuckets) {
          bucket.setItemsFilter(filter);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private class AsyncClusterListener implements ClusterListener {
    @Override
    public void onClusterEvent(ClusterEvent event, ClusterInfo clusterInfo) {
      String otherNodeNameListKey = toolkitInstanceFactory.getAsyncNameListKey(name, event.getNode().getId());
      switch (event.getType()) {
        case NODE_LEFT:
          processOtherNode(otherNodeNameListKey);
          break;
        default:
          break;
      }
    }

  }

  private void processOtherNode(String otherNodeNameListKey) {
    if (status == Status.STARTED) {
      Lock lock = toolkitInstanceFactory.getAsyncWriteLock();
      lock.lock();
      try {
        LinkedList<String> nameList = listNamesMap.get(otherNodeNameListKey);
        LinkedList<String> newOwner = listNamesMap.get(nameListKey);
        if (nameList != null) {
          for (String bucketName : nameList) {
            ToolkitList<E> toolkitList = toolkit.getList(bucketName);
            ProcessingBucket<E> bucket = new ProcessingBucket<E>(bucketName, config, toolkitList, cluster, processor,
                                                                 LoggingErrorHandler.getInstance());
            bucket.setItemsFilter(filter);
            bucket.start(false);
            newOwner.add(bucketName);
          }
          listNamesMap.remove(otherNodeNameListKey); // removing buckets from old node
          listNamesMap.put(nameListKey, newOwner); // transferring bucket ownership to new node
        }
      } catch (ExistingRunningThreadException e) {
        LOGGER.warn(e.getMessage());
      } finally {
        lock.unlock();
      }
    }
  }

  private Set<String> determineDeadNodes() {
    Set<String> deadNodes = Collections.EMPTY_SET;
    // check if the all the known nodes still exist in the cluster
    if (cluster != null) {
      deadNodes = new HashSet<String>(listNamesMap.keySet());
      for (ClusterNode node : cluster.getClusterTopology().getNodes()) {
        deadNodes.remove(toolkitInstanceFactory.getAsyncNameListKey(name, node.getId()));
      }
    }
    return deadNodes;
  }

  private static enum Status {
    UNINITIALIZED, STARTED {
      @Override
      final void checkRunning() {
        // All good!
      }
    },
    STOPPED;

    void checkRunning() {
      throw new IllegalStateException("AsyncCoordinator is " + this.name().toLowerCase() + "!");
    }
  }

  @Override
  public long getQueueSize() {
    Lock lock = coordinatorLock;
    lock.lock();
    try {
      status.checkRunning();
      long size = 0;
      for (ProcessingBucket<E> bucket : localBuckets) {
        size += bucket.getWaitCount();
      }
      return size;
    } finally {
      lock.unlock();
    }
  }

}