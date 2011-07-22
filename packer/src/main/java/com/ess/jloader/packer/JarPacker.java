package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.Const;
import com.ess.jloader.packer.consts.ConstClass;
import com.ess.jloader.packer.consts.ConstUtf;
import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.Utils;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class JarPacker {

    private static final int PACKER_VERSION = 1;

    private static final Logger log = Logger.getLogger(JarPacker.class);

    private final Map<String, ClassNode> classMap = new LinkedHashMap<String, ClassNode>();

    private final Config cfg;

    public JarPacker(Config cfg) {
        this.cfg = cfg;
    }

    private void addClass(String className, InputStream inputStream) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Add class: " + className);
        }

        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(inputStream);
        classReader.accept(new ClassElementFilter(classNode, cfg), 0);

        classMap.put(className.replace('.', '/'), classNode);
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

    public Map<String, ClassNode> getClassMap() {
        return classMap;
    }

    public void writeResult(OutputStream output) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(output);

        // Write MAGIC (2)
        dataOut.writeShort(Utils.MAGIC);

        // Write packer version;
        dataOut.writeShort(PACKER_VERSION);

//        int javaVersion = getJavaVersion();
//        if (javaVersion == -1) throw new InvalidClassException();
//
//        // Write version (4)
//        dataOut.writeInt(javaVersion);

//        SortedMap<String, Integer> classCountMap = new TreeMap<String, Integer>(PackUtils.getClassNameUsages(classMap.values()));

        // Write classes and usage counts.
//        DeflaterOutputStream defOut = new DeflaterOutputStream(dataOut);
//        DataOutputStream dataDefOut = new DataOutputStream(defOut);
//        PackUtils.writeClassNames(classCountMap.keySet(), dataDefOut);
//        for (Integer integer : classCountMap.values()) {
//            if (integer > 0xFFFF) throw new InvalidClassException();
//
//            dataDefOut.writeShort(integer);
//        }
//
//        // Write is provided flags (1)
//        for (String className : classCountMap.keySet()) {
//            dataDefOut.write(classMap.containsKey(className) ? 1 : 0);
//        }
//
//        HuffmanUtils.TreeElement tree = HuffmanUtils.buildHuffmanTree(classCountMap);
//        HuffmanOutputStream hOut = new HuffmanOutputStream(tree);
//
//        List<ByteArrayOutputStream> packedClasses = new ArrayList<ByteArrayOutputStream>();
//
//        for (String className : classCountMap.keySet()) {
//            AClass aClass = classMap.get(className);
//            if (aClass != null) {
//                ByteArrayOutputStream packedClass = packClass(className, aClass, hOut);
//                packedClasses.add(packedClass);
//
////                System.out.println(className + "=" + packedClass.size() + " / " + aClass.getCode().length);
//
//                if (packedClass.size() < 0xFFFF) {
//                    dataDefOut.writeShort(packedClass.size());
//                }
//                else {
//                    dataDefOut.writeShort(0xFFFF);
//                    dataDefOut.writeInt(packedClass.size() - 0xFFFF);
//                }
//            }
//        }
//
//        defOut.finish();
//
//        if (packedClasses.size() != classMap.size()) throw new InvalidClassException();
//
//        for (ByteArrayOutputStream packedClass : packedClasses) {
//            packedClass.writeTo(output);
//        }
    }

    private static byte[] packClass(String name, ClassNode aClass, HuffmanOutputStream hOut) {
        ClassWriter writer = new ClassWriter(0);
        aClass.accept(writer);
        return writer.toByteArray();
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
        JarPacker packer = new JarPacker(new Config());
        packer.addJar(src);
        packer.writeResult(dest);
    }

}
