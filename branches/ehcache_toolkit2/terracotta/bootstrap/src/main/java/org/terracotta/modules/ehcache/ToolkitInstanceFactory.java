/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache;

import net.sf.ehcache.Ehcache;

import org.terracotta.modules.ehcache.event.CacheEventNotificationMsg;
import org.terracotta.modules.ehcache.store.CacheConfigChangeNotificationMsg;
import org.terracotta.toolkit.Toolkit;
import org.terracotta.toolkit.collections.ToolkitMap;
import org.terracotta.toolkit.concurrent.locks.ToolkitReadWriteLock;
import org.terracotta.toolkit.events.ToolkitNotifier;
import org.terracotta.toolkit.internal.collections.ToolkitCacheWithMetadata;

import java.io.Serializable;

/**
 * Factory used for creating {@link Toolkit} instances used for implementing clustered ehcache
 */
public interface ToolkitInstanceFactory {

  Toolkit getToolkit();

  /**
   * Returns a fully qualified name for the cache
   */
  String getFullyQualifiedCacheName(Ehcache cache);

  /**
   * Returns the backend {@link ToolkitCacheWithMetadata} to be used for the cache
   */
  ToolkitCacheWithMetadata<Object, Serializable> getOrCreateToolkitCache(Ehcache cache);

  /**
   * Returns a {@link ToolkitNotifier} for the cache for notifying {@link CacheConfigChangeNotificationMsg} across the
   * cluster
   */
  ToolkitNotifier<CacheConfigChangeNotificationMsg> getOrCreateConfigChangeNotifier(Ehcache cache);

  /**
   * Returns a {@link ToolkitMap} for storing search attribute types
   * 
   * @throws UnsupportedOperationException if search is not supported
   */
  ToolkitMap<String, String> getOrCreateSearchAttributeTypesMap(Ehcache cache);

  /**
   * Returns a {@link ToolkitReadWriteLock} for protecting the cache's store cluster wide
   */
  ToolkitReadWriteLock getOrCreateStoreLock(Ehcache cache);

  /**
   * Returns a {@link ToolkitNotifier} for the cachse to notify {@link CacheEventNotificationMsg} across the cluster
   */
  ToolkitNotifier<CacheEventNotificationMsg> getOrCreateCacheEventNotifier(Ehcache cache);

  /**
   * Returns a {@link ToolkitMap} for storing serialized extractors for the cache
   * 
   * @throws UnsupportedOperationException if search is not supported
   */
  ToolkitMap<String, byte[]> getOrCreateSerializedExtractorsMap(Ehcache cache);

  void shutdown();

}