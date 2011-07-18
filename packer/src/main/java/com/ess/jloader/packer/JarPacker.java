package com.ess.jloader.packer;

import com.ess.jloader.utils.Utils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Sergey Evdokimov
 */
public class JarPacker {

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

    public void printStatistic() throws IOException {
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

    private void writeClassNames(OutputStream out, SortedMap<String, Integer> classNameUsages) throws IOException {
        DeflaterOutputStream defOut = new DeflaterOutputStream(out);
        DataOutputStream dataOut = new DataOutputStream(defOut);
        PackUtils.writeClassNames(classNameUsages.keySet(), dataOut);
        for (Integer integer : classNameUsages.values()) {
            if (integer > 0xFFFF) throw new InvalidClassException();

            dataOut.writeShort(integer);
        }
        defOut.finish();
    }

    public void writeResult(OutputStream output) throws IOException {
        DataOutputStream out = new DataOutputStream(output);
        out.writeShort(Utils.MAGIC);

        int javaVersion = getJavaVersion();
        if (javaVersion == -1) throw new InvalidClassException();

        out.writeInt(javaVersion);

        SortedMap<String, Integer> classCountMap = new TreeMap<String, Integer>(PackUtils.getClassNameUsages(classMap.values()));
        writeClassNames(out, classCountMap);

        String[] classNames = classMap.keySet().toArray(new String[classMap.size()]);
        Arrays.sort(classNames);


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
