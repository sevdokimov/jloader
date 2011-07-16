package com.ess.jloader.packer;

import com.ess.jloader.loader.Utils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class JarPacker {

    private static final Logger log = Logger.getLogger(JarPacker.class);

    private final Map<String, AClass> classMap;

    public JarPacker() {
        classMap = new HashMap<String, AClass>();
    }

    private void addClass(String className, InputStream inputStream) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Add class: " + className);
        }

        classMap.put(className, AClass.createFromCode(inputStream));
    }

    public void addJar(File jarFile) throws IOException {
        JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jarFile));
        try {
            addJar(jarInputStream);
        } finally {
            jarInputStream.close();
        }
    }

    public void addJar(JarInputStream jarInputStream) throws IOException {
        JarEntry entry = jarInputStream.getNextJarEntry();
        while (entry != null) {
            if (!entry.isDirectory()) {
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    String className = Utils.fileNameToClassName(entry.getName());
                    addClass(className, jarInputStream);
                }
            }

            entry = jarInputStream.getNextJarEntry();
        }
    }

    public void writeResult(OutputStream output) throws IOException {
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);

        DataOutputStream dataOutputStream = new DataOutputStream(gzipOutputStream);

        for (Map.Entry<String, AClass> entry : classMap.entrySet()) {
            AClass aClass = entry.getValue();
            dataOutputStream.writeInt(aClass.getCode().length);
            dataOutputStream.writeUTF(entry.getKey());
            aClass.store(dataOutputStream);
        }

        dataOutputStream.writeInt(-1);

        gzipOutputStream.finish();
    }

    public void writeResult(File file) throws IOException {
        OutputStream fileOut = new FileOutputStream(file);
        try {
            writeResult(fileOut);
        } finally {
            fileOut.close();
        }

    }

    public static void pack(File src, File dest) throws IOException {
        JarPacker packer = new JarPacker();
        packer.addJar(src);
        packer.writeResult(dest);
    }

}
