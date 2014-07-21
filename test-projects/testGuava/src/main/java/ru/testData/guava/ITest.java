package ru.testData.guava;

/**
 * @author Sergey Evdokimov
 */
public abstract class ITest {

    protected void assertTrue(boolean f) {
        if (!f) throw new RuntimeException();
    }

    protected void assertFalse(boolean f) {
        if (f) throw new RuntimeException();
    }

    protected void assertNotNull(Object o) {
        if (o == null) throw new RuntimeException();
    }

    protected void assertEquals(Object a, Object b) {
        if (a == null ? b != null : !a.equals(b)) {
            throw new RuntimeException(a + " != " + b);
        }
    }
}
