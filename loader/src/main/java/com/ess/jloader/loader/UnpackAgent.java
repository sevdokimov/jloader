package com.ess.jloader.loader;

import com.ess.jloader.utils.BitInputStream;
import com.ess.jloader.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * @author Sergey Evdokimov
 */
public class UnpackAgent implements ClassFileTransformer {

    private static boolean initialized;

    private volatile PackClassLoader classLoader;

    public UnpackAgent() {

    }

    private PackClassLoader getClassLoader(ClassLoader l) {
        PackClassLoader res = classLoader;
        if (res == null) {
            synchronized (this) {
                res = classLoader;
                if (res == null) {
                    try {
                        File jar = Utils.getJarByMarker(l, PackClassLoader.METADATA_ENTRY_NAME);

                        res = new PackClassLoader(null, jar);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    classLoader = res;
                }
            }
        }

        return res;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (loader == null || classfileBuffer.length < 4) return null;

        if ((classfileBuffer[0] & 0xFF) == 0xCA
                && (classfileBuffer[1] & 0xFF) == 0xFE
                && (classfileBuffer[2] & 0xFF) == 0xBA
                && (classfileBuffer[3] & 0xFF) == 0xBE) {
            return null;
        }

        try {
            ByteArrayInputStream byteIn = new ByteArrayInputStream(classfileBuffer);

            int plainSize = Utils.readShortInt(new DataInputStream(byteIn));
            int pos = plainSize < 0x8000 ? 2 : 4;

            BitInputStream in = new BitInputStream(classfileBuffer, pos, pos + plainSize);

            byteIn.skip(plainSize);

            PackClassLoader packClassLoader = getClassLoader(loader);
            return packClassLoader.unpackClass(in, byteIn, className.replace('.', '/'));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void premain(String options, Instrumentation inst) {
        // Handle duplicate agents
        if (initialized) {
            return;
        }
        initialized = true;

        inst.addTransformer(new UnpackAgent());
    }

}
