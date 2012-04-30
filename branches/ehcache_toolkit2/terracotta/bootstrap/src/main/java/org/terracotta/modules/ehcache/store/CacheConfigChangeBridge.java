/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.store;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.CacheConfigurationListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.toolkit.cluster.ClusterNode;
import org.terracotta.toolkit.config.ToolkitCacheConfigFields;
import org.terracotta.toolkit.config.ToolkitMapConfigFields;
import org.terracotta.toolkit.events.ToolkitNotificationListener;
import org.terracotta.toolkit.events.ToolkitNotifier;
import org.terracotta.toolkit.internal.collections.ToolkitCacheWithMetadata;

import java.io.Serializable;

public class CacheConfigChangeBridge implements CacheConfigurationListener, ToolkitNotificationListener {

  private static final Logger            LOG = LoggerFactory.getLogger(CacheConfigChangeBridge.class);

  private final ToolkitNotifier          notifier;
  private final ToolkitCacheWithMetadata backend;
  private final Ehcache                  cache;
  private final String                   fullyQualifiedEhcacheName;

  public CacheConfigChangeBridge(Ehcache cache, String fullyQualifiedEhcacheName, ToolkitCacheWithMetadata backend,
                                 ToolkitNotifier<CacheConfigChangeNotificationMsg> notifier) {
    this.cache = cache;
    this.fullyQualifiedEhcacheName = fullyQualifiedEhcacheName;
    this.backend = backend;
    this.notifier = notifier;
  }

  public void connectConfigs() {
    cache.getCacheConfiguration().addConfigurationListener(this);
    notifier.addNotificationListener(this);
  }

  public void disconnectConfigs() {
    cache.getCacheConfiguration().removeConfigurationListener(this);
    notifier.removeNotificationListener(this);
  }

  private void changeAndNotify(DynamicConfigType type, Serializable newValue) {
    backend.setConfigField(type.getToolkitConfigName(), newValue);
    notifier.notifyListeners(new CacheConfigChangeNotificationMsg(fullyQualifiedEhcacheName, type
        .getToolkitConfigName(), newValue));
  }

  @Override
  public void timeToIdleChanged(long oldTimeToIdle, long newTimeToIdle) {
    changeAndNotify(DynamicConfigType.MAX_TTI_SECONDS, newTimeToIdle);
  }

  @Override
  public void timeToLiveChanged(long oldTimeToLive, long newTimeToLive) {
    changeAndNotify(DynamicConfigType.MAX_TTL_SECONDS, newTimeToLive);
  }

  @Override
  public void diskCapacityChanged(int oldCapacity, int newCapacity) {
    changeAndNotify(DynamicConfigType.MAX_TOTAL_COUNT, newCapacity);
  }

  @Override
  public void memoryCapacityChanged(int oldCapacity, int newCapacity) {
    changeAndNotify(DynamicConfigType.MAX_COUNT_LOCAL_HEAP, newCapacity);
  }

  @Override
  public void maxBytesLocalHeapChanged(long oldValue, long newValue) {
    changeAndNotify(DynamicConfigType.MAX_BYTES_LOCAL_HEAP, newValue);
  }

  @Override
  public void maxBytesLocalDiskChanged(long oldValue, long newValue) {
    // not supported
  }

  @Override
  public void loggingChanged(boolean oldValue, boolean newValue) {
    // TODO: should this be supported?
  }

  @Override
  public void registered(CacheConfiguration config) {
    // noop
  }

  @Override
  public void deregistered(CacheConfiguration config) {
    // noop
  }

  @Override
  public void onNotification(ToolkitNotifier notifierParam, ClusterNode remoteNode, Serializable msg) {
    if (shouldProcessNotification(notifierParam, msg)) {
      processConfigChangeNotification((CacheConfigChangeNotificationMsg) msg);
    } else {
      LOG.warn("Ignoring uninterested notification - notifier: " + notifierParam + ", remoteNode: " + remoteNode
               + ", msg: " + msg);
    }
  }

  private void processConfigChangeNotification(CacheConfigChangeNotificationMsg notification) {
    try {
      DynamicConfigType type = DynamicConfigType.getTypeFromToolkitConfigName(notification.getToolkitConfigName());
      Object newValue = notification.getNewValue();
      switch (type) {
        case MAX_TTI_SECONDS: {
          cache.getCacheConfiguration().internalSetTimeToIdle(getLong(newValue));
          break;
        }
        case MAX_TTL_SECONDS: {
          cache.getCacheConfiguration().internalSetTimeToLive(getLong(newValue));
          break;
        }
        case MAX_TOTAL_COUNT: {
          cache.getCacheConfiguration().internalSetDiskCapacity(getInt(newValue));
          break;
        }
        case MAX_COUNT_LOCAL_HEAP: {
          cache.getCacheConfiguration().internalSetMemCapacity(getInt(newValue));
          break;
        }
        case MAX_BYTES_LOCAL_HEAP: {
          cache.getCacheConfiguration().internalSetMemCapacityInBytes(getLong(newValue));
          break;
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.warn("Notification will be ignored. Caught IllegalArgumentException while processing notification: "
               + notification + ", exception: " + e.getMessage());
    }
  }

  private boolean shouldProcessNotification(ToolkitNotifier notifierParam, Serializable msg) {
    return notifier == notifierParam && msg instanceof CacheConfigChangeNotificationMsg
           && ((CacheConfigChangeNotificationMsg) msg).getFullyQualifiedEhcacheName().equals(fullyQualifiedEhcacheName);
  }

  private static enum DynamicConfigType {
    MAX_TOTAL_COUNT(ToolkitCacheConfigFields.MAX_TOTAL_COUNT_FIELD_NAME), MAX_COUNT_LOCAL_HEAP(
        ToolkitMapConfigFields.MAX_COUNT_LOCAL_HEAP_FIELD_NAME), MAX_BYTES_LOCAL_HEAP(
        ToolkitMapConfigFields.MAX_BYTES_LOCAL_HEAP_FIELD_NAME), MAX_TTI_SECONDS(
        ToolkitCacheConfigFields.MAX_TTI_SECONDS_FIELD_NAME), MAX_TTL_SECONDS(
        ToolkitCacheConfigFields.MAX_TTL_SECONDS_FIELD_NAME);

    private final String toolkitConfigName;

    private DynamicConfigType(String toolkitConfigName) {
      this.toolkitConfigName = toolkitConfigName;
    }

    public String getToolkitConfigName() {
      return toolkitConfigName;
    }

    public static DynamicConfigType getTypeFromToolkitConfigName(final String toolkitName) {
      for (DynamicConfigType type : DynamicConfigType.values()) {
        if (type.getToolkitConfigName().equals(toolkitName)) { return type; }
      }
      throw new IllegalArgumentException("Unknown toolkit config name - " + toolkitName);
    }
  }

  private static long getLong(Object newValue) {
    if (newValue instanceof Integer) {
      return ((Integer) newValue).intValue();
    } else if (newValue instanceof Long) {
      return ((Long) newValue).longValue();
    } else {
      throw new IllegalArgumentException("Expected long value but got: " + newValue);
    }
  }

  private static int getInt(Object newValue) {
    if (newValue instanceof Integer) {
      return ((Integer) newValue).intValue();
    } else {
      throw new IllegalArgumentException("Expected int value but got: " + newValue);
    }
  }
}