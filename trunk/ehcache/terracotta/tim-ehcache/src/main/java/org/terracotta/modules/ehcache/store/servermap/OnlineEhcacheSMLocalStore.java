/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.modules.ehcache.store.servermap;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;
import net.sf.ehcache.terracotta.InternalEhcache;

import com.terracotta.toolkit.collections.servermap.api.ServerMapLocalStoreFullException;
import com.terracotta.toolkit.collections.servermap.api.ServerMapLocalStoreListener;
import com.terracotta.toolkit.collections.servermap.api.adapters.ServerMapLocalStoreAdapter;

import java.util.List;

public class OnlineEhcacheSMLocalStore extends ServerMapLocalStoreAdapter<Object, Object> {

  private final InternalEhcache localStoreCache;

  public OnlineEhcacheSMLocalStore(InternalEhcache localStoreCache) {
    this.localStoreCache = localStoreCache;
  }

  @Override
  public boolean addListener(ServerMapLocalStoreListener<Object, Object> listener) {
    return localStoreCache.getCacheEventNotificationService()
        .registerListener(new ServerMapLocalStoreEhcacheListenerAdapter(listener));
  }

  @Override
  public boolean removeListener(ServerMapLocalStoreListener<Object, Object> listener) {
    return localStoreCache.getCacheEventNotificationService()
        .unregisterListener(new ServerMapLocalStoreEhcacheListenerAdapter(listener));
  }

  @Override
  public Object get(Object key) {
    Element element = localStoreCache.get(key);
    return element == null ? null : element.getObjectValue();
  }

  @Override
  public List<Object> getKeys() {
    return localStoreCache.getKeys();
  }

  @Override
  public Object put(Object key, Object value) throws ServerMapLocalStoreFullException {
    try {
      Element element = localStoreCache.get(key);
      localStoreCache.put(new Element(key, value));
      return element == null ? null : element.getObjectValue();
    } catch (CacheException e) {
      handleCacheException(e);
      throw e;
    }
  }

  @Override
  public Object replace(Object key, Object oldValue, Object newValue) {
    Element element = localStoreCache.get(key);
    if (element == null || !oldValue.equals(element.getObjectValue())) { return null; }

    if (!localStoreCache.remove(key)) { return null; }

    Element newElement = new Element(key, newValue);
    newElement.setEternal(element.isEternal());
    localStoreCache.put(newElement);
    return element.getObjectValue();
  }

  @Override
  public Object remove(Object key) {
    Element element = localStoreCache.removeAndReturnElement(key);
    return element == null ? null : element.getObjectValue();
  }

  @Override
  public Object remove(Object key, Object value) {
    Element element = localStoreCache.get(key);
    if (element == null || !value.equals(element.getObjectValue())) { return null; }
    boolean removed = localStoreCache.remove(key);
    if (removed) { return element.getObjectValue(); }
    return null;
  }

  @Override
  public int getMaxEntriesLocalHeap() {
    return (int) localStoreCache.getCacheConfiguration().getMaxEntriesLocalHeap();
  }

  @Override
  public void setMaxEntriesLocalHeap(int newValue) {
    localStoreCache.getCacheConfiguration().setMaxEntriesLocalHeap(newValue);
  }

  private void handleCacheException(CacheException ce) throws ServerMapLocalStoreFullException, CacheException {
    Throwable rootCause = getRootCause(ce);
    if (rootCause.getClass().getName().contains("OversizeMappingException")
        || rootCause.getClass().getName().contains("CrossPoolEvictionException")) {
      throw new ServerMapLocalStoreFullException();
    } else {
      throw ce;
    }
  }

  private Throwable getRootCause(Throwable throwable) {
    Throwable t = throwable;
    if (t == null) { throw new AssertionError("Tried to find the root cause of null"); }
    while (t.getCause() != null) {
      t = t.getCause();
    }
    return t;
  }

  @Override
  public void unpinAll() {
    localStoreCache.unpinAll();
  }

  @Override
  public boolean isPinned(Object key) {
    return localStoreCache.isPinned(key);
  }

  @Override
  public void setPinned(Object key, boolean pinned) {
    localStoreCache.setPinned(key, pinned);
  }

  @Override
  public void clear() {
    localStoreCache.removeAll();
  }

  @Override
  public long getOnHeapSizeInBytes() {
    return localStoreCache.calculateInMemorySize();
  }

  @Override
  public long getOffHeapSizeInBytes() {
    return localStoreCache.calculateOffHeapSize();
  }

  @Override
  public int getOffHeapSize() {
    return (int) localStoreCache.getOffHeapStoreSize();
  }

  @Override
  public int getOnHeapSize() {
    return (int) localStoreCache.getMemoryStoreSize();
  }

  @Override
  public int getSize() {
    return localStoreCache.getSize();
  }

  @Override
  public void dispose() {
    //
  }

  @Override
  public boolean containsKeyOnHeap(Object key) {
    return localStoreCache.isElementInMemory(key);
  }

  @Override
  public boolean containsKeyOffHeap(Object key) {
    // Offheap has everything in the local cache, so we just need to verify that the key is anywhere in the cache
    return localStoreCache.isKeyInCache(key);
  }

  @Override
  public void setMaxBytesLocalHeap(long newMaxBytesLocalHeap) {
    localStoreCache.getCacheConfiguration().setMaxBytesLocalHeap(newMaxBytesLocalHeap);
  }

  @Override
  public long getMaxBytesLocalHeap() {
    return localStoreCache.getCacheConfiguration().getMaxBytesLocalHeap();
  }

  @Override
  public void recalculateSize(Object key) {
    localStoreCache.recalculateSize(key);
  }

}