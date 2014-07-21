package ru.testData.guava;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class TestLists extends ITest {

    public void testLists() {
        checkList(Lists.asList(1, new Integer[]{1, 2}));
        checkList(Lists.asList(1, 2, new Integer[]{1, 2}));
        checkList(Lists.charactersOf("abc"));
        checkList(Lists.partition(Lists.newArrayList(1, 2, 3), 2));
    }

    private void checkList(List<?> l) {
        assertTrue(l.size() > 0);
        assertNotNull(l.iterator());
        assertNotNull(l.listIterator());

        assertNotNull(l.subList(0, 0));
    }
}
