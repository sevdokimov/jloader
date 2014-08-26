package com.ess.jloader.packer.tests;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.utils.Utils;
import com.google.common.collect.Ordering;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class TestUtils {

    public static File getJarByMarker(String marker) {
        return Utils.getJarByMarker(Thread.currentThread().getContextClassLoader(), marker);
    }

    public static File createTmpPackFile(String prefix) {
        return new File(System.getProperty("java.io.tmpdir") + "/" + prefix + ".j");
    }

    public static void printFirst(final Map<String, Long> map, int count) {
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());

            if (--count == 0) break;
        }
    }

    public static <T> Map<T, Long> sort(final Map<T, Long> map) {
        List<T> keys = new ArrayList<T>(map.keySet());

        Collections.sort(keys, Ordering.from(new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                return (int)(map.get(o1) - map.get(o2));
            }
        }).reverse());

        Map<T, Long> res = new LinkedHashMap<T, Long>();
        for (T key : keys) {
            res.put(key, map.get(key));
        }

        return res;
    }

    public static void runJar(File jar) throws Exception {
        ClassLoader l = new PackClassLoader(null, jar);
        runJar(l);
    }

    public static void runJar(ClassLoader l) throws Exception {
        ClassLoader savedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(l);

        try {
            Class aClass = l.loadClass("Main");

            Method main = aClass.getMethod("main", new Class[]{String[].class});

            main.invoke(null, (Object)new String[0]);

        } finally {
            Thread.currentThread().setContextClassLoader(savedContextClassLoader);
        }
    }

}
