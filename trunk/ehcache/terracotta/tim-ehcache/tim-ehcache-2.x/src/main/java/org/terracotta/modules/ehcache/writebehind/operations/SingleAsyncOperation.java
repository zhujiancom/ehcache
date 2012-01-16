/**
 * Copyright 2003-2009 Terracotta, Inc. Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package org.terracotta.modules.ehcache.writebehind.operations;

import net.sf.ehcache.writer.CacheWriter;

import org.terracotta.cache.serialization.SerializationStrategy;

import java.io.IOException;
import java.util.List;

/**
 * Interface to implement single operations that are performed in the write behind implementation that using an
 * AsyncCoordinator underneath
 * 
 * @author Geert Bevin
 * @version $Id: SingleAsyncOperation.java 29067 2011-08-02 10:07:00Z alexsnaps $
 */
public interface SingleAsyncOperation {
  /**
   * Perform this operation as a single execution with the provided cache writer
   * 
   * @param cacheWriter the cache writer this operation should be performed upon
   * @param serializationStrategy the strategy that should be used to serialize and deserialize, if needed
   */
  public void performSingleOperation(CacheWriter cacheWriter, SerializationStrategy serializationStrategy)
      throws ClassNotFoundException, IOException;

  /**
   * Creates a batch operation that corresponds to the operation type of this single async operation.
   * <p/>
   * This batch operation will not be stored in the async queue anymore and is solely used for structuring. The data
   * from the single operation will already be processed in the final form that will be expected by the {@code
   * CacheWriter} that will be used to execute the batch operation.
   * 
   * @param operations the single operations that need to be regrouped in the batch operation
   * @param serializationStrategy the serialization strategy that should be used to create the data for the batch
   *        operation
   * @return the created batch operation
   */
  public BatchAsyncOperation createBatchOperation(List<SingleAsyncOperation> operations,
                                                  SerializationStrategy serializationStrategy)
      throws ClassNotFoundException, IOException;

  /**
   * Retrieves the key for this operation.
   * 
   * @param serializationStrategy the serialization strategy that should be used to create the key
   * @return this operation's key
   */
  Object getKey(SerializationStrategy serializationStrategy) throws IOException, ClassNotFoundException;

  /**
   * Retrieves the moment when the operation was created.
   * 
   * @return the creation time in milliseconds
   */
  public long getCreationTime();

  /**
   * When all else failed, this method will be called to throw the item away.
   *
   * @param cacheWriter
   * @param serializationStrategy
   * @param e
   * @throws ClassNotFoundException
   * @throws IOException
   */
  void throwAwayElement(CacheWriter cacheWriter, SerializationStrategy serializationStrategy, RuntimeException e) throws ClassNotFoundException, IOException;
}