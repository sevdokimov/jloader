package com.ess.jloader.packer;

import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.HuffmanUtils;
import com.ess.jloader.utils.Utils;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.AtomicLongMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.objectweb.asm.ClassReader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Sergey Evdokimov
 */
public class JarMetaData {

    private final Map<String, Integer> stringsMap = new LinkedHashMap<String, Integer>();

    private final HuffmanUtils.TreeElement huffmanTreeRoot;

    private final List<Integer> versions = new ArrayList<Integer>(8);

    public JarMetaData(Map<String, ClassReader> classMap) throws InvalidJarException {
        AtomicLongMap<String> stringsCountMap = AtomicLongMap.create();

        for (Map.Entry<String, ClassReader> entry : classMap.entrySet()) {
            ClassReader classReader = entry.getValue();

            Integer version = classReader.readInt(4);
            if (!versions.contains(version)) {
                if (versions.size() == 8) {
                    throw new InvalidJarException();
                }
                versions.add(version);
            }

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

        List<String> keys = new ArrayList<String>(stringsCountMap.asMap().keySet());
        Collections.sort(keys, new Comparator<String>() {
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

        huffmanTreeRoot = HuffmanUtils.buildHuffmanTree(stringsMap);
    }

    @Nullable
    private static String extractClassName(String s) {
        if (PackUtils.CLASS_JVM_QNAME_PATTERN.matcher(s).matches()) {
            return s;
        }

        Matcher matcher = PackUtils.CLASS_TYPE_PATTERN.matcher(s);
        if (matcher.matches()) {
            return matcher.group(1);
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

    public HuffmanOutputStream createHuffmanOutput() {
        return new HuffmanOutputStream(huffmanTreeRoot);
    }

    public int getVersionIndex(int version) {
        int res = versions.indexOf(version);
        assert res >= 0 : version;
        return res;
    }

    public void writeTo(OutputStream out) throws IOException {
        DataOutputStream o = new DataOutputStream(out);

        o.write(Utils.MAGIC); // Magic
        o.write(Utils.PACKER_VERSION);

        for (int i = 0; i < 8; i++) {
            int x = 0;
            if (i < versions.size()) {
                x = versions.get(i);
            }
            o.writeInt(x);
        }

        o.writeInt(stringsMap.size());
        for (String s : stringsMap.keySet()) {
            o.writeUTF(s);
        }

        for (Integer integer : stringsMap.values()) {
            if (integer > 0xFFFF) {
                throw new InvalidJarException();
            }

            o.writeShort(integer);
        }
    }
}
