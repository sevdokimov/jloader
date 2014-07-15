package com.ess.jloader.packer;

import com.ess.jloader.utils.Utils;
import com.google.common.util.concurrent.AtomicLongMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class JarMetaData {

    private final Map<String, Integer> stringsMap = new LinkedHashMap<String, Integer>();

    public JarMetaData(Map<String, ClassReader> classMap) throws InvalidClassException {
        AtomicLongMap<String> stringsCountMap = AtomicLongMap.create();

        for (Map.Entry<String, ClassReader> entry : classMap.entrySet()) {
            ClassReader classReader = entry.getValue();

            for (int i = 0; i < classReader.getItemCount() - 1; i++) {
                int pos = classReader.getItem(i + 1);

                if (pos > 0 && classReader.b[pos - 1] == 1) {
                    String s = PackUtils.readUtf(classReader, pos);

                    stringsCountMap.incrementAndGet(s);
//                    if (!s.equals(classReader.getClassName())) {
//                    }
                }
            }
        }

        for (Map.Entry<String, Long> entry : stringsCountMap.asMap().entrySet()) {
            if (entry.getValue() > 1) {
                stringsMap.put(entry.getKey(), stringsMap.size());
            }
        }
    }

    @Nullable
    public Integer getStringIndex(String s) {
        return stringsMap.get(s);
    }

    public void writeTo(OutputStream out) throws IOException {
        DataOutputStream o = new DataOutputStream(out);

        o.write(Utils.MAGIC); // Magic
        o.write(Utils.PACKER_VERSION);

        o.writeInt(stringsMap.size());
        for (String s : stringsMap.keySet()) {
            o.writeUTF(s);
        }
    }
}
