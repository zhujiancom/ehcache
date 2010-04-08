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

package net.sf.ehcache.hibernate.management.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.commons.beanutils.BeanUtils;
import org.hibernate.stat.QueryStatistics;

/**
 * @author gkeim
 *
 */
public class QueryStats implements Serializable {
  private static final String        COMPOSITE_TYPE_NAME        = "QueryStats";
  private static final String        COMPOSITE_TYPE_DESCRIPTION = "Statistics per Query";
  private static final String[]      ITEM_NAMES                 = new String[] {"query", "cacheHitCount",
      "cacheMissCount", "cachePutCount", "executionCount", "executionRowCount", "executionAvgTime", "executionMaxTime",
      "executionMinTime", };
  private static final String[]      ITEM_DESCRIPTIONS          = new String[] {"query", "cacheHitCount",
      "cacheMissCount", "cachePutCount", "executionCount", "executionRowCount", "executionAvgTime", "executionMaxTime",
      "executionMinTime", };
  private static final OpenType[]    ITEM_TYPES                 = new OpenType[] {SimpleType.STRING, SimpleType.LONG,
      SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG,
      SimpleType.LONG, };
  private static final CompositeType COMPOSITE_TYPE;
  private static final String        TABULAR_TYPE_NAME          = "Statistics by Query";
  private static final String        TABULAR_TYPE_DESCRIPTION   = "All Query Statistics";
  private static final String[]      INDEX_NAMES                = new String[] {"query", };
  private static final TabularType   TABULAR_TYPE;

  static {
    try {
      COMPOSITE_TYPE = new CompositeType(COMPOSITE_TYPE_NAME, COMPOSITE_TYPE_DESCRIPTION, ITEM_NAMES,
                                         ITEM_DESCRIPTIONS, ITEM_TYPES);
      TABULAR_TYPE = new TabularType(TABULAR_TYPE_NAME, TABULAR_TYPE_DESCRIPTION, COMPOSITE_TYPE, INDEX_NAMES);
    } catch (OpenDataException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * query
   */
  protected final String query;
  
  /**
   * cacheHitCount
   */
  protected long cacheHitCount;
  
  /**
   * cacheMissCount
   */
  protected long cacheMissCount;
  
  /**
   * cachePutCount
   */
  protected long cachePutCount;
  
  /**
   * executionCount
   */
  protected long executionCount;
  
  /**
   * executionRowCount
   */
  protected long executionRowCount;
  
  /**
   * executionAvgTime
   */
  protected long executionAvgTime;
  
  /**
   * executionMaxTime
   */
  protected long executionMaxTime;
  
  /**
   * executionMinTime
   */
  protected long executionMinTime;

  /**
   * @param name
   */
  public QueryStats(String name) {
    this.query = name;
  }

  /**
   * @param name
   * @param src
   */
  public QueryStats(String name, QueryStatistics src) {
    this(name);
    
    Map map;
    try {
        map = BeanUtils.describe(src);
    } catch (Exception e) {
        throw new RuntimeException("Describing QueryStatistics bean", e);
    }
    
    this.cacheHitCount = safeParseInt((String)map.get("cacheHitCount"));
    this.cacheMissCount = safeParseInt((String)map.get("cacheMissCount"));
    this.cachePutCount = safeParseInt((String)map.get("cachePutCount"));
    this.executionCount = safeParseInt((String)map.get("executionCount"));
    this.executionRowCount = safeParseInt((String)map.get("executionRowCount"));
    this.executionAvgTime = safeParseInt((String)map.get("executionAvgTime"));
    this.executionMaxTime = safeParseInt((String)map.get("executionMaxTime"));
    this.executionMinTime = safeParseInt((String)map.get("executionMinTime"));
  }

  /**
   * @param cData
   */
  public QueryStats(final CompositeData cData) {
    int i = 0;
    query = (String) cData.get(ITEM_NAMES[i++]);
    cacheHitCount = (Long) cData.get(ITEM_NAMES[i++]);
    cacheMissCount = (Long) cData.get(ITEM_NAMES[i++]);
    cachePutCount = (Long) cData.get(ITEM_NAMES[i++]);
    executionCount = (Long) cData.get(ITEM_NAMES[i++]);
    executionRowCount = (Long) cData.get(ITEM_NAMES[i++]);
    executionAvgTime = (Long) cData.get(ITEM_NAMES[i++]);
    executionMaxTime = (Long) cData.get(ITEM_NAMES[i++]);
    executionMinTime = (Long) cData.get(ITEM_NAMES[i++]);
  }

  private static int safeParseInt(String s) {
      try {
          return Integer.parseInt(s);
      } catch (Exception e) {
          return -1;
      }
  }
  
  /**
   * @param stats
   */
  public void add(QueryStats stats) {
    cacheHitCount += stats.getCacheHitCount();
    cacheMissCount += stats.getCacheMissCount();
    cachePutCount += stats.getCachePutCount();
    executionCount += stats.getExecutionCount();
    executionRowCount += stats.getExecutionRowCount();
    executionAvgTime += stats.getExecutionAvgTime();
    executionMaxTime += stats.getExecutionMaxTime();
    executionMinTime += stats.getExecutionMinTime();
  }

  /**
   * toString
   */
  @Override
  public String toString() {
    return "query=" + query + ", cacheHitCount=" + cacheHitCount + ", cacheMissCount=" + cacheMissCount
           + ", cachePutCount=" + cachePutCount + ", executionCount=" + executionCount + ", executionRowCount="
           + executionRowCount + ", executionAvgTime=" + executionAvgTime + ", executionMaxTime=" + executionMaxTime
           + ", executionMinTime=" + executionMinTime;
  }

  /**
   * getQuery
   */
  public String getQuery() {
    return query;
  }

  /**
   * getCacheHitCount
   */
  public long getCacheHitCount() {
    return cacheHitCount;
  }

  /**
   * getCacheMissCount
   */
  public long getCacheMissCount() {
    return cacheMissCount;
  }

  /**
   * getCachePutCount
   */
  public long getCachePutCount() {
    return cachePutCount;
  }

  /**
   * getExecutionCount
   */
  public long getExecutionCount() {
    return executionCount;
  }

  /**
   * getExecutionRowCount
   */
  public long getExecutionRowCount() {
    return executionRowCount;
  }

  /**
   * getExecutionAvgTime
   */
  public long getExecutionAvgTime() {
    return executionAvgTime;
  }

  /**
   * getExecutionMaxTime
   */
  public long getExecutionMaxTime() {
    return executionMaxTime;
  }

  /**
   * getExecutionMinTime
   */
  public long getExecutionMinTime() {
    return executionMinTime;
  }

  /**
   * toCompositeData
   */
  public CompositeData toCompositeData() {
    try {
      return new CompositeDataSupport(COMPOSITE_TYPE, ITEM_NAMES, new Object[] {query, cacheHitCount, cacheMissCount,
          cachePutCount, executionCount, executionRowCount, executionAvgTime, executionMaxTime, executionMinTime, });
    } catch (OpenDataException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * newTabularDataInstance
   */
  public static TabularData newTabularDataInstance() {
    return new TabularDataSupport(TABULAR_TYPE);
  }

  /**
   * fromTabularData
   */
  public static QueryStats[] fromTabularData(final TabularData tabularData) {
    final List<QueryStats> countList = new ArrayList(tabularData.size());
    for (final Iterator pos = tabularData.values().iterator(); pos.hasNext();) {
      countList.add(new QueryStats((CompositeData) pos.next()));
    }
    return countList.toArray(new QueryStats[countList.size()]);
  }
}
