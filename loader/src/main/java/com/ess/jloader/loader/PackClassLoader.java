package com.ess.jloader.loader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

/**
 * @author Sergey Evdokimov
 */
public class PackClassLoader extends ClassLoader {

    private File[] files;

    public PackClassLoader(File ... files) {
        this.files = files;
    }

    public PackClassLoader(ClassLoader parent) {
        super(parent);
        String property = System.getProperty("jloader.cp");
        if (property == null) throw new RuntimeException("jloader.cp not defined");

        List<File> res = new ArrayList<File>();

        for (StringTokenizer st = new StringTokenizer(property, ";"); st.hasMoreTokens(); ) {
            String path = st.nextToken();
            File file = new File(path);
            if (!file.isFile()) throw new RuntimeException("File not found: " + file + " (jloader.cp=" + property + ')');

            res.add(file);
        }

        load(res);
    }

    private void load(Collection<File> files) {
        this.files = files.toArray(new File[files.size()]);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
//        System.out.println("Loading class \"" + name + "\"...");
        try {
            for (File file : files) {
                DataInputStream inputStream = new DataInputStream(new GZIPInputStream(new FileInputStream(file)));
                try {
                    do {
                        int size = inputStream.readInt();
                        if (size == -1) {
                            break;
                        }

                        String string = inputStream.readUTF();
                        if (name.equals(string)) {
                            byte[] aClassData = new byte[size];
                            inputStream.readFully(aClassData);

                            Class<?> res = defineClass(name, aClassData, 0, aClassData.length);
//                            System.out.println("Class \"" + name + "\" loaded!");
                            return res;
                        }

                        inputStream.skipBytes(size);
                    } while (true);
                }
                finally {
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            throw new ClassNotFoundException("", e);
        }

        throw new ClassNotFoundException();
    }
}
