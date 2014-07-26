package com.ess.jloader.packer;

import com.ess.jloader.utils.HuffmanUtils;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.AtomicLongMap;
import org.objectweb.asm.ClassReader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class VersionCache {

    private final List<Integer> versions = new ArrayList<Integer>(8);

    public VersionCache(Collection<ClassDescriptor> classes) throws InvalidJarException {
        for (ClassDescriptor classDescriptor : classes) {
            Integer version = classDescriptor.getClassReader().readInt(4);
            if (!versions.contains(version)) {
                if (versions.size() == 8) {
                    throw new InvalidJarException();
                }
                versions.add(version);
            }
        }
    }

    public int getVersionIndex(int version) {
        int res = versions.indexOf(version);
        assert res >= 0 : version;
        return res;
    }

    public void writeTo(DataOutputStream out) throws IOException {
        for (int i = 0; i < 8; i++) {
            int x = 0;
            if (i < versions.size()) {
                x = versions.get(i);
            }
            out.writeInt(x);
        }
    }
}
