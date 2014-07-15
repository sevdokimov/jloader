package com.ess.jloader.packer;

import com.ess.jloader.utils.Utils;
import com.google.common.io.ByteStreams;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * @author Sergey Evdokimov
 */
public class JarPacker {

    private static final int PACKER_VERSION = 1;

    private static final Logger log = Logger.getLogger(JarPacker.class);

    private final Map<String, ClassReader> classMap = new LinkedHashMap<String, ClassReader>();

    private final Map<String, byte[]> resourceMap = new LinkedHashMap<String, byte[]>();

    private final Map<String, JarEntry> resourceEntries = new LinkedHashMap<String, JarEntry>();

    private Manifest manifest;

    private final Config cfg;

    public JarPacker(Config cfg) {
        this.cfg = cfg;
    }

    private void addClass(String className, InputStream inputStream) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Add class: " + className);
        }

        ClassReader classReader = new ClassReader(inputStream);

        classMap.put(className, classReader);
    }

    public void addJar(File jarFile) throws IOException {
        JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jarFile));

        if (manifest == null) {
            manifest = jarInputStream.getManifest();
        }

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
                else {
                    resourceMap.put(entry.getName(), ByteStreams.toByteArray(jarInputStream));
                }
            }

            resourceEntries.put(entry.getName(), entry);

            entry = jarInputStream.getNextJarEntry();
        }
    }

    private void pack(ClassReader classReader, OutputStream output) throws IOException {
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        classReader.accept(classWriter, 0);

        byte[] classBytes = classWriter.toByteArray();
        Arrays.fill(classBytes, 0, 4, (byte) 0);

        output.write(classBytes);
    }

    public void pack(OutputStream output) throws IOException {
        JarOutputStream zipOutputStream;

        if (manifest != null) {
            zipOutputStream = new JarOutputStream(output, manifest);
        }
        else {
            zipOutputStream = new JarOutputStream(output);
        }

        try {
            for (Map.Entry<String, JarEntry> entry : resourceEntries.entrySet()) {
                JarEntry jarEntry = entry.getValue();

                jarEntry.setCompressedSize(-1);

                zipOutputStream.putNextEntry(jarEntry);

                if (!jarEntry.isDirectory()) {
                    byte[] resourceContent = resourceMap.get(jarEntry.getName());
                    if (resourceContent != null) {
                        zipOutputStream.write(resourceContent);
                    }
                    else {
                        String className = Utils.fileNameToClassName(jarEntry.getName());

                        ClassReader classReader = classMap.get(className);
                        pack(classReader, zipOutputStream);
                    }
                }

                zipOutputStream.closeEntry();
            }
        } finally {
            zipOutputStream.close();
        }
    }

    public void writeResult(File file) throws IOException {
        OutputStream fileOut = new FileOutputStream(file);
        try {
            pack(fileOut);
        } finally {
            fileOut.close();
        }

    }

    public static void pack(File src, File dest) throws IOException {
        JarPacker packer = new JarPacker(new Config());
        packer.addJar(src);
        packer.writeResult(dest);
    }

    public Map<String, ClassReader> getClassMap() {
        return classMap;
    }
}
