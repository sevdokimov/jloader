package com.ess.jloader.packer;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static void getTable(@NotNull ClassNode classNode) {
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
}
