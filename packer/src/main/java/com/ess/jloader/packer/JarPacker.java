package com.ess.jloader.packer;

import com.ess.jloader.loader.Utils;
import org.apache.commons.io.IOUtils;

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

    private final Map<String, byte[]> classMap;

    public JarPacker() {
        classMap = new HashMap<String, byte[]>();
    }

    private void addClass(String className, InputStream inputStream) throws IOException {
        byte[] bytes = IOUtils.toByteArray(inputStream);
        classMap.put(className, bytes);
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

        for (Map.Entry<String, byte[]> entry : classMap.entrySet()) {
            byte[] classCode = entry.getValue();
            dataOutputStream.writeInt(classCode.length);
            dataOutputStream.writeUTF(entry.getKey());
            dataOutputStream.write(classCode);
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
