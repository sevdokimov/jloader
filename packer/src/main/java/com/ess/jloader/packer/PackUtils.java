package com.ess.jloader.packer;

import com.ess.jloader.loader.PackClassLoader;
import com.ess.jloader.packer.consts.AbstractConst;
import com.ess.jloader.packer.consts.ConstDouble;
import com.ess.jloader.packer.consts.ConstLong;
import com.ess.jloader.utils.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * @author Sergey Evdokimov
 */
public class PackUtils {

    public static final Pattern TYPE_PATTERN = Pattern.compile("(\\[*)(?:([BCDFIJSZ])|L(([\\w\\$]+)(/[\\w\\$]+)*);)");

    public static final Pattern CLASS_NAME_PATTERN = Pattern.compile("[\\w\\$]+(/[\\w\\$]+)*");

    public static final Pattern CLASS_QNAME_PATTERN = Pattern.compile("([\\w]+\\.)+([A-Z][\\w\\$]*)");
    public static final Pattern CLASS_JVM_QNAME_PATTERN = Pattern.compile("([\\w]+/)+([A-Z][\\w\\$]*)");

    public static final Pattern CLASS_TYPE_PATTERN = Pattern.compile("\\[*L((?:[\\w]+/)+[A-Z][\\w\\$]*);");

    public static final Pattern CLASS_NAME_OR_TYPE_PATTERN = Pattern.compile("\\[*L((?:[\\w]+/)+[A-Z][\\w\\$]*);|(?:[\\w]+/)+[A-Z][\\w\\$]*");

    public static final Pattern METHOD_DESCR_PATTERN = Pattern.compile("\\((?:\\[*(?:[BCDFIJSZ]|L(?:[\\w]+/)+[A-Z][\\w\\$]*;))*\\)\\[*(?:[BCDFIJSZV]|L(?:[\\w]+/)+[A-Z][\\w\\$]*;)");

    private PackUtils() {

    }

    public static void write(OutputStream out, ByteBuffer codeBuffer, int len) throws IOException {
        int position = codeBuffer.position();
        out.write(codeBuffer.array(), position, len);
        codeBuffer.position(position + len);
    }

    public static void writeLimitedNumber(DataOutputStream out, int x, int limit) throws IOException {
        assert x >= 0;
        assert x <= limit;

        if (Utils.CHECK_LIMITS) {
            out.writeShort(limit);
        }

        if (limit == 0) {
            // data no needed
        }
        else if (limit < 256) {
            out.write(x);
        }
        else if (limit < 256*3) {
            writeSmallShort3(out, x);
        }
        else {
            out.writeShort(x);
        }
    }

    public static void writeSmallSignedShort(DataOutput out, int x) throws IOException {
        if (x > 127 || x < -127) {
            out.write(-128);
            out.writeShort(x);
        }
        else {
            out.write(x);
        }
    }

    public static void writeSmallShort3(DataOutput out, int x) throws IOException {
        assert x <= 0xFFFF;

        if (Utils.CHECK_LIMITS) {
            out.writeByte(0x73);
        }

        if (x <= 251) {
            out.write(x);
        }
        else {
            int z = x + 4;
            int d = 251 + (z >> 8);

            if (d < 255) {
                out.write(d);
                out.write(z);
            }
            else {
                out.write(255);
                out.writeShort(x);
            }
        }
    }

    private static class Package {
        private List<String> classes = new ArrayList<String>();

        private Map<String, Package> packages = new TreeMap<String, Package>();
    }

    public static int sizeTypes = 0;
    public static int sizeClasses = 0;

    public static List<String> extractTypes(String methodSign) throws InvalidJarException {
        int closedBracketIndex = methodSign.lastIndexOf(')');
        if (closedBracketIndex == -1) throw new InvalidJarException();

        List<String> res = new ArrayList<String>();

        String returnType = methodSign.substring(closedBracketIndex + 1);
        if (!returnType.equals("V")) {
            assertType(returnType);
            res.add(returnType);
        }

        if (closedBracketIndex > 1) {
            String params = methodSign.substring(1, closedBracketIndex);
            Matcher m = TYPE_PATTERN.matcher(params);
            int idx = 0;
            while (m.find()) {
                if (m.start() != idx) {
                    throw new InvalidJarException(methodSign);
                }

                res.add(m.group());
                idx = m.end();
            }

            if (idx != params.length()) throw new InvalidJarException(methodSign);
        }

        return res;
    }

    private static <T> void inc(Map<T, Integer> map, T key) {
        Integer value = map.get(key);
        if (value == null) {
            map.put(key, 1);
        }
        else {
            map.put(key, value + 1);
        }
    }

    public static int summ(Map<?, Integer> map) {
        int res = 0;

        for (Integer integer : map.values()) {
            res += integer;
        }

        return res;
    }

    public static void assertType(String s) throws InvalidJarException {
        if (!TYPE_PATTERN.matcher(s).matches()) {
            throw new InvalidJarException();
        }
    }

    public static Map<String, Integer> writeClassNames(Collection<String> classNames, DataOutput out) throws IOException {
        assertSorted(classNames);

        Package root = new Package();

        for (String className : classNames) {
            if (!CLASS_NAME_PATTERN.matcher(className).matches()) throw new InvalidJarException(className);

            Package p = root;

            for (StringTokenizer st = new StringTokenizer(className, "/"); ; ) {
                String s = st.nextToken();

                if (!st.hasMoreTokens()) {
                    p.classes.add(s);
                    break;
                }
                else {
                    Package next = p.packages.get(s);
                    if (next == null) {
                        next = new Package();
                        p.packages.put(s, next);
                    }

                    p = next;
                }
            }
        }

        writePackages(out, root);
        writeClasses(out, root);

        return null;
    }

    private static void writePackages(DataOutput out, Package p) throws IOException {
        for (Map.Entry<String, Package> entry : p.packages.entrySet()) {
            out.write(entry.getKey().getBytes());
            out.write(',');
            writePackages(out, entry.getValue());
        }

        out.write(',');
    }

    private static <T extends Comparable<T>> void assertSorted(Collection<T> c) {
        if (c.isEmpty()) return;

        Iterator<T> iterator = c.iterator();
        T prev = iterator.next();

        while (iterator.hasNext()) {
            T e = iterator.next();
            assert prev.compareTo(e) < 0;
            prev = e;
        }
    }

    private static void writeClasses(DataOutput out, Package p) throws IOException {
        assertSorted(p.classes);

        for (String className : p.classes) {
            out.write(className.getBytes());
            out.write(',');
        }

        out.write(',');

        for (Package subPackage : p.packages.values()) {
            writeClasses(out, subPackage);
        }
    }

    public static String readUtf(ClassReader classReader, int offset) {
        return readUtf(classReader.b, offset);
    }

    public static String readUtf(byte[] buffer, int offset) {
        try {
            return new DataInputStream(new ByteArrayInputStream(buffer, offset, buffer.length)).readUTF();
        } catch (IOException e) {
            throw new InvalidJarException(e);
        }
    }

    public static byte[] readBytes(ByteBuffer buffer, int length) {
        int newPos = buffer.position() + length;
        byte[] res = Arrays.copyOfRange(buffer.array(), buffer.position(), newPos);
        buffer.position(newPos);
        return res;
    }

    public static int evaluateAnonymousClassCount(ClassNode cn) {
        Set<Integer> anonymousIndexes = new HashSet<Integer>();
        int maxAnonymousClassIndex = 0;
        for (InnerClassNode innerClass : cn.innerClasses) {
            if (innerClass.innerName == null) {
                int dollarIndex = innerClass.name.lastIndexOf('$');
                if (dollarIndex == cn.name.length() && innerClass.name.startsWith(cn.name)) {
                    try {
                        int index = Integer.parseInt(innerClass.name.substring(dollarIndex + 1));

                        if (index == 0) {
                            return 0;
                        }

                        if (index > maxAnonymousClassIndex) {
                            maxAnonymousClassIndex = index;
                        }
                        anonymousIndexes.add(index);
                    } catch (NumberFormatException ignored) {

                    }
                }
            }
        }

        for (int i = 1; i < maxAnonymousClassIndex; i++) {
            if (!anonymousIndexes.contains(i)) {
                return 0;
            }
        }

        return maxAnonymousClassIndex;
    }

    public static ClassReader repack(ClassReader classReader) {
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(classWriter, 0);
        return new ClassReader(classWriter.toByteArray());
    }

    public static int getConstPoolSize(Collection<AbstractConst> consts) {
        int res = consts.size() + 1;

        for (AbstractConst aConst : consts) {
            if (aConst.getTag() == ConstLong.TAG || aConst.getTag() == ConstDouble.TAG) {
                res++;
            }
        }

        return res;
    }

    public static boolean isPackedJar(File jar) throws IOException {
        ZipFile zipFile = new ZipFile(jar);

        try {
            return zipFile.getEntry(PackClassLoader.METADATA_ENTRY_NAME) != null;
        } finally {
            zipFile.close();
        }
    }
}
