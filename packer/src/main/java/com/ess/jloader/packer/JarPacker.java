package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.Const;
import com.ess.jloader.packer.consts.ConstClass;
import com.ess.jloader.packer.consts.ConstUtf;
import com.ess.jloader.utils.HuffmanOutputStream;
import com.ess.jloader.utils.HuffmanUtils;
import com.ess.jloader.utils.Utils;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import org.apache.log4j.Logger;

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

    private final Map<String, AClass> classMap;

    private final Map<String, ClassDescriptor> allClassesMap = new HashMap<String, ClassDescriptor>();

    public JarPacker() {
        classMap = new LinkedHashMap<String, AClass>();
    }

    private void addClass(String className, InputStream inputStream) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Add class: " + className);
        }

        classMap.put(className.replace('.', '/'), AClass.createFromCode(inputStream));
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

    public void printStatistic() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        SortedMap<String, Integer> classCountMap = new TreeMap<String, Integer>(PackUtils.getClassNameUsages(classMap.values()));

//        DataOutputStream dataOutputStream = new DataOutputStream(out);
//
//        writeClassNames(out, classCountMap);

        Map.Entry<String, Integer>[] entries = classCountMap.entrySet().toArray(new Map.Entry[classCountMap.size()]);

        Arrays.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o1.getValue() - o2.getValue();
            }
        });

//        for (Map.Entry<String, Integer> entry : classCountMap.entrySet()){
//            System.out.println(entry.getKey() + "=" + entry.getValue());
//        }

//        for (Map.Entry<String, Integer> entry : entries) {
//            System.out.println(entry.getKey() + "=" + entry.getValue());
//        }

        System.out.println(out.size());
        System.out.println(classCountMap.size());

    }

    private int getJavaVersion() {
        if (classMap.isEmpty()) return 50;

        Iterator<AClass> itr = classMap.values().iterator();

        int res = itr.next().getJavaVersion();

        while (itr.hasNext()) {
            if (res != itr.next().getJavaVersion()) {
                return -1;
            }
        }

        return res;
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

        SortedMap<String, Integer> classCountMap = new TreeMap<String, Integer>(PackUtils.getClassNameUsages(classMap.values()));

        // Write classes and usage counts.
        DeflaterOutputStream defOut = new DeflaterOutputStream(dataOut);
        DataOutputStream dataDefOut = new DataOutputStream(defOut);
        PackUtils.writeClassNames(classCountMap.keySet(), dataDefOut);
        for (Integer integer : classCountMap.values()) {
            if (integer > 0xFFFF) throw new InvalidClassException();

            dataDefOut.writeShort(integer);
        }

        // Write is provided flags (1)
        for (String className : classCountMap.keySet()) {
            dataDefOut.write(classMap.containsKey(className) ? 1 : 0);
        }

        HuffmanUtils.TreeElement tree = HuffmanUtils.buildHuffmanTree(classCountMap);
        HuffmanOutputStream hOut = new HuffmanOutputStream(tree);

        List<ByteArrayOutputStream> packedClasses = new ArrayList<ByteArrayOutputStream>();

        for (String className : classCountMap.keySet()) {
            AClass aClass = classMap.get(className);
            if (aClass != null) {
                ByteArrayOutputStream packedClass = packClass(className, aClass, hOut);
                packedClasses.add(packedClass);

//                System.out.println(className + "=" + packedClass.size() + " / " + aClass.getCode().length);

                if (packedClass.size() < 0xFFFF) {
                    dataDefOut.writeShort(packedClass.size());
                }
                else {
                    dataDefOut.writeShort(0xFFFF);
                    dataDefOut.writeInt(packedClass.size() - 0xFFFF);
                }
            }
        }

        defOut.finish();

        if (packedClasses.size() != classMap.size()) throw new InvalidClassException();

        for (ByteArrayOutputStream packedClass : packedClasses) {
            packedClass.writeTo(output);
        }
    }

    private static ByteArrayOutputStream packClass(String name, AClass aClass, HuffmanOutputStream hOut) {
        try {
            Set<ConstUtf> classNames = new HashSet<ConstUtf>();

            for (Const aConst : aClass.getConsts()) {
                if (aConst instanceof ConstClass) {
                    ConstUtf nameConst = ((ConstClass) aConst).getName().get();
                    if (!nameConst.getText().startsWith("[")) {
                        classNames.add(nameConst);
                    }
                }
            }

            ByteArrayOutputStream data = new ByteArrayOutputStream();

            new DataOutputStream(data).writeInt(aClass.getJavaVersion());

            DeflaterOutputStream defOut = new DeflaterOutputStream(data);
            DataOutputStream dataOut = new DataOutputStream(defOut);

            ByteArrayOutputStream hByteOut = new ByteArrayOutputStream();
            hOut.reset(hByteOut);

            for (Const aConst : aClass.getConsts()) {
                if (aConst == null) continue;

                if (classNames.contains(aConst)) {
                    dataOut.write(20);
                    hOut.write(((ConstUtf)aConst).getText());
                }
                else {
                    dataOut.write(aConst.getCode());
                    aConst.writeTo(dataOut);
                }
            }

            hOut.finish();

            dataOut.write(21);

            defOut.write(aClass.getCode(), aClass.getConstTableEnd(), aClass.getCode().length - aClass.getConstTableEnd());
            defOut.close();

            ByteArrayOutputStream res = new ByteArrayOutputStream(4 + data.size() + hByteOut.size());
            new DataOutputStream(res).writeInt(data.size());
            data.writeTo(res);
            hByteOut.writeTo(res);

            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
