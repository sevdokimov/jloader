package com.ess.jloader.packer.tests;

import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.AtomicLongMap;

import java.io.File;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class TestUtils {

    public static File getJarByMarker(String marker) {
        String path = Thread.currentThread().getContextClassLoader().getResource(marker).getPath();
        int idx = path.lastIndexOf("!/");
        assert idx > 0;

        path = path.substring(0, idx);
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }

        return new File(path);
    }

    public static File createTmpPAckFile(String prefix) {
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

}
