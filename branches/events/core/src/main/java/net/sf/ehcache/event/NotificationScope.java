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

package net.sf.ehcache.event;

/**
 * This enumeration defines valid values for event listener notification scope.
 * By default an event listener will be open to listening for events created 
 * on all nodes (ALL).  You may also specify to receive only LOCAL events or only
 * REMOTE events.
 * @author Alex Miller
 */
public enum NotificationScope {
  /** Receive only events generated by this CacheManager */
  LOCAL, 

  /** Receive only events generated by another CacheManager */
  REMOTE, 
  
  /** Receive all events */
  ALL;
}
