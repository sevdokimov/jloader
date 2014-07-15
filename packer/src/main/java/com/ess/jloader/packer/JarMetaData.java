package com.ess.jloader.packer;

import com.ess.jloader.utils.Utils;
import com.google.common.util.concurrent.AtomicLongMap;
import org.objectweb.asm.ClassReader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class JarMetaData {

    private final AtomicLongMap<String> stringsMap = AtomicLongMap.create();

    public JarMetaData(Map<String, ClassReader> classMap) throws InvalidClassException {
        for (Map.Entry<String, ClassReader> entry : classMap.entrySet()) {
            ClassReader classReader = entry.getValue();

        }
    }

    public void writeTo(OutputStream out) throws IOException {
        DataOutputStream o = new DataOutputStream(out);

        o.write(Utils.MAGIC); // Magic
        o.write(Utils.PACKER_VERSION);
    }
}
