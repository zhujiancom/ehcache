/**
 *  Copyright 2003-2007 Greg Luck
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

/**
 * Test cases for the Element.
 *
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id$
 */
public class ElementTest extends AbstractCacheTest {
    private static final Log LOG = LogFactory.getLog(ElementTest.class.getName());


    /**
     * Checks serialization performance.
     * <p/>
     * {@link Element#getSerializedSize()} measures size by serializing, so this
     * can be used to measure JVM serialization speed.
     * <p/>
     * For 310232 bytes the average serialization time is 7 ms
     */
    public void testSerializationPerformanceByteArray() throws CacheException {
        Serializable key = "key";

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        for (int j = 0; j < 10000; j++) {
            try {
                bout.write("abcdefghijklmnopqrstv1234567890".getBytes());
            } catch (IOException e) {
                LOG.error("This should not happen");
            }
        }
        byte[] value = bout.toByteArray();

        Element element = new Element(key, value);
        StopWatch stopWatch = new StopWatch();
        for (int i = 0; i < 100; i++) {
            element.getSerializedSize();
        }
        long elapsed = stopWatch.getElapsedTime() / 100;
        LOG.info("In-memory size in bytes: " + element.getSerializedSize()
                + " time to serialize in ms: " + elapsed);
        assertTrue("Large object clone takes more than than 100ms", elapsed < 100);
    }


    /**
     * Checks the serialization time for a large compound Java object
     * Serialization time was 126ms for a size of 349225
     */
    public void testSerializationPerformanceJavaObjects() throws Exception {
        //Set up object graphs
        HashMap map = new HashMap(10000);
        for (int j = 0; j < 10000; j++) {
            map.put("key" + j, new String[]{"adfdafs", "asdfdsafa", "sdfasdf"});
        }
        Element element = new Element("key1", map);
        StopWatch stopWatch = new StopWatch();
        for (int i = 0; i < 100; i++) {
            element.getSerializedSize();
        }
        long elapsed = stopWatch.getElapsedTime() / 100;
        LOG.info("In-memory size in bytes: " + element.getSerializedSize()
                + " time to serialize in ms: " + elapsed);
        assertTrue("Large object clone took more than 500ms", elapsed < 500);
    }


    /**
     * Checks the expense of cloning a large object
     * Average clone time 175ms for a size of 349225
     */
    public void testCalculateClonePerformanceJavaObjects() throws Exception {
        //Set up object graphs
        HashMap map = new HashMap(10000);
        for (int j = 0; j < 10000; j++) {
            map.put("key" + j, new String[]{"adfdafs", "asdfdsafa", "sdfasdf"});
        }
        Element element = new Element("key1", map);
        StopWatch stopWatch = new StopWatch();
        for (int i = 0; i < 100; i++) {
            element.clone();
        }
        long elapsed = stopWatch.getElapsedTime() / 100;
        LOG.info("Time to clone object in ms: " + elapsed);
        LOG.info("In-memory size in bytes: " + element.getSerializedSize()
                + " time to clone in ms: " + elapsed);
        assertTrue("Large object clone takes less than 1 second", elapsed < 1000);
    }

    /**
     * Checks serialization performance.
     * <p/>
     * {@link Element#getSerializedSize()} measures size by serializing, so this
     * can be used to measure JVM serialization speed.
     * <p/>
     * For 310232 bytes the average clone time is 50 ms, but on Mac JDK 1.5 i seems to have blown out to 116ms. It looks
     * like a performance regression.
     * <p/>
     */
    public void testClonePerformanceByteArray() throws CacheException, CloneNotSupportedException {
        Serializable key = "key";

        byte[] value = getTestByteArray();

        Element element = new Element(key, value);
        StopWatch stopWatch = new StopWatch();
        for (int i = 0; i < 100; i++) {
            element.clone();
        }
        long elapsed = stopWatch.getElapsedTime() / 100;
        LOG.info("In-memory size in bytes: " + element.getSerializedSize()
                + " time to serialize in ms: " + elapsed);
        assertTrue("Large object clone takes less than 130 milliseconds", elapsed < 180);
    }


    private byte[] getTestByteArray() {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        for (int j = 0; j < 10000; j++) {
            try {
                bout.write("abcdefghijklmnopqrstv1234567890".getBytes());
            } catch (IOException e) {
                LOG.error("This should not happen");
            }
        }
        return bout.toByteArray();

    }

    /**
     * Tests the deserialization performance of an element containing a large byte[]
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void testDeserializationPerformance() throws IOException, ClassNotFoundException {

        byte[] value = getTestByteArray();
        Element element = new Element("test", value);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bout);
        oos.writeObject(element);
        byte[] serializedValue = bout.toByteArray();
        oos.close();
        StopWatch stopWatch = new StopWatch();
        for (int i = 0; i < 100; i++) {
            ByteArrayInputStream bin = new ByteArrayInputStream(serializedValue);
            ObjectInputStream ois = new ObjectInputStream(bin);
            ois.readObject();
            ois.close();
        }
        long elapsed = stopWatch.getElapsedTime() / 100;
        LOG.info("In-memory size in bytes: " + serializedValue.length
                + " time to deserialize in ms: " + elapsed);
        assertTrue(elapsed < 30);
    }


    /**
     * ehcache-1.2 adds support to Objects in addition to Serializable. Check that this works
     */
    public void testObjectAccess() {
        Object key = new Object();
        Object value = new Object();
        Element element = new Element(key, value);
        //Should work
        assertEquals(key, element.getObjectKey());
        assertEquals(value, element.getObjectValue());

        //Should fail
        try {
            element.getKey();
        } catch (CacheException e) {
            //expected
        }
        assertEquals(value, element.getObjectValue());

    }

    /**
     * ehcache-1.1 and earllier exclusively uses Serializable keys and values. Check that this works
     */
    public void testSerializableAccess() {
        Serializable key = "";
        Serializable value = "";
        Element element = new Element(key, value);

        //test gets
        assertEquals(key, element.getObjectKey());
        assertEquals(value, element.getObjectValue());

        //should also work with objects
        assertEquals(key, element.getObjectKey());
        assertEquals(value, element.getObjectValue());


    }

    /**
     * Tests that isSerializable does not blow up is if either key or value is null
     * A null, null will not be serializable
     */
    public void testIsSerializable() {

        Element element = new Element(null, null);
        assertFalse(element.isKeySerializable());
        assertFalse(element.isSerializable());


        Element elementWithNullValue = new Element("1", null);
        assertFalse(elementWithNullValue.isSerializable());


        Object object = null;
        assertFalse(object instanceof Serializable);

    }

    /**
     * Tests the robustness of equals
     */
    public void testEquals() {

        Element element = new Element("key", "value");
        assertFalse(element.equals("dog"));
        assertTrue(element.equals(element));
        assertFalse(element.equals(null));
        assertFalse(element.equals(new Element("cat", "hat")));
    }

    /**
     * Tests that the full constructor sets everything right.
     */
    public void testFullConstructor() {

        Element element = new Element("key", "value", 1L, 123L, 1234L, 12345L, 123456L, 1234567L);
        assertEquals("key", element.getKey());
        assertEquals("value", element.getValue());
        assertEquals(1L, element.getVersion());
        assertEquals(123L, element.getCreationTime());
        assertEquals(1234L, element.getLastAccessTime());
        assertEquals(12345L, element.getNextToLastAccessTime());
        assertEquals(123456L, element.getLastUpdateTime());
        assertEquals(1234567L, element.getHitCount());

    }


}
