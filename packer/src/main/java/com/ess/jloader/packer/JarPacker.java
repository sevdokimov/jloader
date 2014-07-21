package com.ess.jloader.packer;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.ess.jloader.utils.Utils;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
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
        byte[] dictionary = PackUtils.buildDictionary(packedItems);

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

    private static class ClassDescriptor {
        public final ClassReader classReader;

        public OpenByteOutputStream plainDataArray;
        public OpenByteOutputStream forCompressionDataArray;

        public ClassDescriptor(ClassReader classReader) {
            this.classReader = classReader;
        }

        private void pack(CompressionContext ctx) throws IOException {
            plainDataArray = new OpenByteOutputStream();
            forCompressionDataArray = new OpenByteOutputStream();

            DataOutputStream plainData = new DataOutputStream(plainDataArray);
            DataOutputStream compressed = new DataOutputStream(forCompressionDataArray);

            String className = classReader.getClassName();

            Set<String> packedStr = new LinkedHashSet<String>();

            List<String> utfInConstPool = new ArrayList<String>();

            for (int i = 1; i < classReader.getItemCount(); i++) {
                int pos = classReader.getItem(i);
                if (pos == 0) continue;

                if (classReader.b[pos - 1] == 1) {
                    String s = PackUtils.readUtf(classReader.b, pos);

                    if (s.equals(className)) continue; // skip name of current class

                    if (ctx.getLiteralsCache().getHasString(s)) {
                        packedStr.add(s);
                    }
                    else {
                        utfInConstPool.add(s);
                    }
                }

                if (packedStr.size() == 0xFFFF) break;
            }

            ClassWriter classWriter = new ClassWriter(0);
            classWriter.newClass(className);
            for (String s : packedStr) {
                classWriter.newUTF8(s);
            }

            Collections.sort(utfInConstPool);
            for (String s : utfInConstPool) {
                classWriter.newUTF8(s);
            }

            classReader.accept(classWriter, 0);

            byte[] classBytes = classWriter.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(classBytes);

            int flags = 0;

            int version = buffer.getInt(4);
            flags |= ctx.getVersionCache().getVersionIndex(version);

            if (classBytes.length > 0xFFFF) {
                flags |= Utils.F_LONG_CLASS;
            }

            buffer.position(4); // skip 0xCAFEBABE

            buffer.getInt(); // skip version

            int constCount = buffer.getShort() & 0xFFFF;

            plainData.writeInt(flags);

            if (classBytes.length > 0xFFFF) {
                plainData.writeInt(classBytes.length);
            }
            else {
                plainData.writeShort(classBytes.length);
            }

            plainData.writeShort(constCount);

            plainData.writeShort(packedStr.size());
            HuffmanOutputStream<String> h = ctx.getLiteralsCache().createHuffmanOutput();
            h.reset(plainData);
            for (String s : packedStr) {
                h.write(s);
            }
            h.finish();

            // First const is class name
            skipUtfConst(buffer, className);
            if (buffer.get() != 7) throw new RuntimeException();
            if (buffer.getShort() != 1) throw new RuntimeException();

            for (String s : packedStr) {
                skipUtfConst(buffer, s);
            }

            copyConstTableTail(buffer, constCount - 1 - packedStr.size() - 2, compressed);

            int accessFlags = buffer.getShort();
            compressed.writeShort(accessFlags);

            int thisClassIndex = buffer.getShort();
            if (thisClassIndex != 2) throw new RuntimeException(String.valueOf(thisClassIndex));

            compressed.write(classBytes, buffer.position(), classBytes.length - buffer.position());
        }

        public void writeTo(OutputStream out, byte[] dictionary) throws IOException {
            plainDataArray.writeTo(out);

            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            deflater.setDictionary(dictionary);

            DeflaterOutputStream defOut = new DeflaterOutputStream(out, deflater);
            forCompressionDataArray.writeTo(defOut);
            defOut.close();
        }

        private void skipUtfConst(ByteBuffer buffer, String value) {
            int tag = buffer.get();
            if (tag != 1) throw new RuntimeException("" + tag);
            if (!value.equals(PackUtils.readUtf(buffer.array(), buffer.position()))) {
                throw new RuntimeException();
            }

            int strSize = buffer.getShort();
            buffer.position(buffer.position() + strSize);
        }

        private void copyConstTableTail(ByteBuffer buffer, int constCount, DataOutputStream out) throws IOException {
            int oldPosition = buffer.position();

            for (int i = 0; i < constCount; i++) {
                int tag = buffer.get();

                int size;
                switch (tag) {
                    case 9: // ClassWriter.FIELD:
                    case 10: // ClassWriter.METH:
                    case 11: // ClassWriter.IMETH:
                    case 3: // ClassWriter.INT:
                    case 4: // ClassWriter.FLOAT:
                    case 12: // ClassWriter.NAME_TYPE:
                    case 18: // ClassWriter.INDY:
                        size = 4;
                        break;
                    case 5:// ClassWriter.LONG:
                    case 6: // ClassWriter.DOUBLE:
                        size = 8;
                        ++i;
                        break;
                    case 1: // ClassWriter.UTF8:
                        size = buffer.getShort() & 0xFFFF;
                        break;

                    case 15: // ClassWriter.HANDLE:
                        size = 3;
                        break;
                    // case ClassWriter.CLASS:
                    // case ClassWriter.STR:
                    // case ClassWriter.MTYPE
                    default:
                        size = 2;
                        break;
                }

                buffer.position(buffer.position() + size);
            }

            out.write(buffer.array(), oldPosition, buffer.position() - oldPosition);
        }
    }
}
