package com.ess.jloader.packer;

import com.ess.jloader.utils.HuffmanOutputStream;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.AtomicLongMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.objectweb.asm.ClassReader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Sergey Evdokimov
 */
public class LiteralsCache {

    private final Map<String, Integer> stringsMap = new LinkedHashMap<String, Integer>();

    private final Map<String, boolean[]> huffmanPathMap;

    public LiteralsCache(Collection<ClassReader> classes) throws InvalidJarException {
        AtomicLongMap<String> stringsCountMap = AtomicLongMap.create();

        for (ClassReader classReader : classes) {
            for (int i = 0; i < classReader.getItemCount() - 1; i++) {
                int pos = classReader.getItem(i + 1);

                if (pos > 0 && classReader.b[pos - 1] == 1) {
                    String s = PackUtils.readUtf(classReader, pos);

                    if (!s.equals(classReader.getClassName())) {
                        stringsCountMap.incrementAndGet(s);
                    }
                }
            }
        }

        String[] keys = stringsCountMap.asMap().keySet().toArray(new String[stringsCountMap.size()]);

        Arrays.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return ComparisonChain.start()
                        .compareTrueFirst(PackUtils.CLASS_QNAME_PATTERN.matcher(o1).matches(), PackUtils.CLASS_QNAME_PATTERN.matcher(o2).matches())
                        .compare(extractClassName(o1), extractClassName(o2), Ordering.natural().nullsLast())
                        .compareTrueFirst(PackUtils.METHOD_DESCR_PATTERN.matcher(o1).matches(), PackUtils.METHOD_DESCR_PATTERN.matcher(o2).matches())
                        .compare(o1, o2)
                        .result();
            }
        });

        for (String s : keys) {
            long count = stringsCountMap.get(s);
            if (count > 1) {
                stringsMap.put(s, (int) count);
            }
        }

        huffmanPathMap = HuffmanOutputStream.buildPathMap(stringsMap);
    }

    @Nullable
    private static String extractClassName(String s) {
        Matcher matcher = PackUtils.CLASS_NAME_OR_TYPE_PATTERN.matcher(s);
        if (matcher.matches()) {
            String res = matcher.group(1);
            if (res == null) {
                res = matcher.group();
            }

            return res;
        }

        return null;
    }

    public boolean getHasString(String s) {
        return stringsMap.containsKey(s);
    }

    @TestOnly
    public Map<String, Integer> getStringsMap() {
        return stringsMap;
    }

    public HuffmanOutputStream<String> createHuffmanOutput() {
        return new HuffmanOutputStream<String>(huffmanPathMap);
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(stringsMap.size());
        for (String s : stringsMap.keySet()) {
            out.writeUTF(s);
        }

        for (Integer integer : stringsMap.values()) {
            if (integer > 0xFFFF) {
                throw new InvalidJarException();
            }

            out.writeShort(integer);
        }
    }
}
