package ru.testData.guava;

import com.google.common.base.Predicates;
import com.google.common.collect.*;
import com.google.common.util.concurrent.AtomicLongMap;

import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class TestMaps extends ITest {

    public void testImmutableMap() {
        ImmutableMap<String, String> map = ImmutableMap.<String, String>builder().put("foo", "1").build();
        checkMap(map);
        assertEquals(map.keySet().iterator().next(), "foo");
    }

    public void testBiMap() {
        ImmutableBiMap<String, String> map = ImmutableBiMap.<String, String>builder().put("foo", "1").build();
        checkMap(map);
        checkMap(map.inverse());
    }

    public void testMaps() {
        AtomicLongMap<String> map = AtomicLongMap.create();
        map.put("foo", 1);

        Maps.filterKeys(map.asMap(), Predicates.alwaysFalse());
        Maps.difference(ImmutableMap.of("1", "1", "2", "2"), ImmutableMap.of("1", "1"));

        Maps.filterValues(HashBiMap.create(), Predicates.alwaysTrue());
    }

    public void testMultiMap() {
        ArrayListMultimap.create().put("1", "2");
        Multimaps.synchronizedMultimap(LinkedListMultimap.create(1)).entries().iterator().hasNext();
        Multimaps.filterKeys(ImmutableMultimap.of("a", "1", "b", "2", "c", "3"), Predicates.not(Predicates.instanceOf(String.class)));
        Multimaps.forMap(Maps.newEnumMap(RetentionPolicy.class)).entries().iterator().hasNext();
    }


    private void checkMap(Map<String, String> m) {
        assertTrue(m.entrySet().size() == m.size());
        assertTrue(m.entrySet().iterator().next().getKey() instanceof String);
        assertTrue(m.keySet().iterator().next() instanceof String);
        assertTrue(m.values().iterator().next() instanceof String);
    }
}
