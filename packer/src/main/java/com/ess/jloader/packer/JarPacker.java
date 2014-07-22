package com.ess.jloader.packer;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class JarPacker {

    private static final Logger log = Logger.getLogger(JarPacker.class);

    private final Map<String, ClassDescriptor> classMap = new LinkedHashMap<String, ClassDescriptor>();

    private final Collection<ClassReader> classReaders = Collections2.transform(classMap.values(), new Function<ClassDescriptor, ClassReader>() {
        @Override
        public ClassReader apply(ClassDescriptor classDescriptor) {
            return classDescriptor.classReader;
        }
    });

    private final Map<String, byte[]> resourceMap = new LinkedHashMap<String, byte[]>();

    private final Map<String, JarEntry> resourceEntries = new LinkedHashMap<String, JarEntry>();

    private Manifest manifest;

    private final Config cfg;

    public JarPacker(Config cfg) {
        this.cfg = cfg;
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
            String fileName = entry.getName();

            if (!entry.isDirectory()) {
                if (fileName.endsWith(".class")) {
                    String className = Utils.fileNameToClassName(fileName);
                    ClassReader classReader = new ClassReader(jarInputStream);
                    if (!classReader.getClassName().equals(className)) throw new InvalidJarException();

                    classMap.put(className, new ClassDescriptor(classReader));
                }
                else {
                    resourceMap.put(fileName, ByteStreams.toByteArray(jarInputStream));
                }
            }

            resourceEntries.put(fileName, entry);

            entry = jarInputStream.getNextJarEntry();
        }
    }

    private void truncClassExtension(JarEntry entry) {
        String oldName = entry.getName();
        if (oldName.endsWith(".class")) {
            try {
                Field nameField = ZipEntry.class.getDeclaredField("name");
                nameField.setAccessible(true);
                nameField.set(entry, oldName.substring(0, oldName.length() - ".class".length() + ".c".length()));
            } catch (Exception e) {
                Throwables.propagate(e);
            }
        }
    }

    private void writeMetadata(ZipOutputStream zipOut, CompressionContext ctx, byte[] dictionary) throws IOException {
        DataOutputStream zipDataOutput = new DataOutputStream(zipOut);

        zipOut.putNextEntry(new ZipEntry(PackClassLoader.METADATA_ENTRY_NAME));

        zipDataOutput.write(Utils.MAGIC); // Magic
        zipDataOutput.write(Utils.PACKER_VERSION);

        ctx.getVersionCache().writeTo(zipDataOutput);
        ctx.getLiteralsCache().writeTo(zipDataOutput);

        zipDataOutput.writeShort(dictionary.length);
        zipDataOutput.write(dictionary);

        zipOut.closeEntry();
    }

    public void pack(OutputStream output) throws IOException {
        CompressionContext ctx = new CompressionContext(classReaders);

        for (ClassDescriptor classDescriptor : classMap.values()) {
            classDescriptor.pack(ctx);
        }

        Collection<OpenByteOutputStream> packedItems = Collections2.transform(classMap.values(), new Function<ClassDescriptor, OpenByteOutputStream>() {
            @Override
            public OpenByteOutputStream apply(ClassDescriptor classDescriptor) {
                return classDescriptor.forCompressionDataArray;
            }
        });
        byte[] dictionary = DictionaryCalculator.buildDictionary(packedItems);

        JarOutputStream zipOutputStream;

        if (manifest != null) {
            zipOutputStream = new JarOutputStream(output, manifest);
        }
        else {
            zipOutputStream = new JarOutputStream(output);
        }

        writeMetadata(zipOutputStream, ctx, dictionary);

        OpenByteOutputStream buff = new OpenByteOutputStream();

        for (Map.Entry<String, JarEntry> entry : resourceEntries.entrySet()) {
            JarEntry jarEntry = entry.getValue();

            jarEntry.setCompressedSize(-1);

            truncClassExtension(jarEntry);

            if (!jarEntry.isDirectory()) {
                byte[] resourceContent = resourceMap.get(jarEntry.getName());
                if (resourceContent != null) {
                    zipOutputStream.putNextEntry(jarEntry);
                    zipOutputStream.write(resourceContent);
                    zipOutputStream.closeEntry();
                }
                else {

                    String className = Utils.fileNameToClassName(entry.getKey());

                    ClassDescriptor classDescriptor = classMap.get(className);
                    buff.reset();
                    classDescriptor.writeTo(buff, dictionary);

                    jarEntry.setMethod(ZipEntry.STORED);
                    jarEntry.setSize(buff.size());
                    jarEntry.setCompressedSize(buff.size());
                    jarEntry.setCrc(Hashing.crc32().hashBytes(buff.getBuffer(), 0, buff.size()).asInt() & 0xFFFFFFFFL);

                    zipOutputStream.putNextEntry(jarEntry);
                    buff.writeTo(zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
            else {
                zipOutputStream.putNextEntry(jarEntry);
                zipOutputStream.closeEntry();
            }
        }

        zipOutputStream.close();
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

    public Collection<ClassReader> getClassReaders() {
        return classReaders;
    }

}
