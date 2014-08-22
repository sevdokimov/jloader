package com.ess.jloader.packer;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.utils.ClassComparator;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;

import java.io.*;
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
            return classDescriptor.getClassReader();
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

    public boolean hasClasses() {
        return classMap.size() > 0;
    }

    public void writeResult(@NotNull File resultFile) throws IOException {
        CompressionContext ctx = new CompressionContext(classMap.values());

        for (ClassDescriptor classDescriptor : classMap.values()) {
            classDescriptor.pack(ctx);
        }

        Collection<OpenByteOutputStream> packedItems = Collections2.transform(classMap.values(), new Function<ClassDescriptor, OpenByteOutputStream>() {
            @Override
            public OpenByteOutputStream apply(ClassDescriptor classDescriptor) {
                return classDescriptor.forCompressionDataArray;
            }
        });
//        byte[] dictionary = new byte[0];
        byte[] dictionary = DictionaryCalculator.buildDictionary(packedItems);

        OutputStream out = new FileOutputStream(resultFile);

        try {
            JarOutputStream zipOutputStream;

            if (manifest != null) {
                zipOutputStream = new JarOutputStream(out, manifest);
            }
            else {
                zipOutputStream = new JarOutputStream(out);
            }

            if (hasClasses()) {
                writeMetadata(zipOutputStream, ctx, dictionary);
            }

            OpenByteOutputStream buff = new OpenByteOutputStream();

            for (Map.Entry<String, JarEntry> entry : resourceEntries.entrySet()) {
                JarEntry jarEntry = entry.getValue();

                jarEntry.setCompressedSize(-1);

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
        } finally {
            out.close();
        }
    }

    public void checkResult(File targetJar) throws IOException {
        if (!hasClasses()) return;

        PackClassLoader loader = new PackClassLoader(null, targetJar);

        for (ClassDescriptor descriptor : classMap.values()) {
            byte[] unpackClass = loader.unpackClass(descriptor.getClassName());
            assert unpackClass != null;

            ClassComparator.compare(descriptor.getRepackedClass(), unpackClass);
        }
    }

    public static void pack(File src, File dest) throws IOException {
        JarPacker packer = new JarPacker(new Config());
        packer.addJar(src);
        packer.writeResult(dest);

        packer.checkResult(dest);
    }

    public Map<String, ClassDescriptor> getClassMap() {
        return classMap;
    }

    public Collection<ClassReader> getClassReaders() {
        return classReaders;
    }

}
