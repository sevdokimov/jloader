package ru.testData.guava;

import com.google.common.base.Predicates;

import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class TestPredicates extends ITest {

    public void testP() {
        assertFalse(
                Predicates.and(Predicates.in(Arrays.asList("1")), Predicates.in(Arrays.asList("2"))).apply("foo")
        );

        assertFalse(
                Predicates.or(Predicates.in(Arrays.asList("1")), Predicates.in(Arrays.asList("2"))).apply("foo")
        );

        assertFalse(
                Predicates.assignableFrom(Integer.class).apply(String.class)
        );

        assertFalse(
                Predicates.assignableFrom(Integer.class).apply(String.class)
        );

    }

}
