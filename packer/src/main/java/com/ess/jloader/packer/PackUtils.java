package com.ess.jloader.packer;

import com.ess.jloader.utils.ByteArrayString;
import com.ess.jloader.utils.OpenByteOutputStream;
import com.google.common.base.Throwables;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.AtomicLongMap;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
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

    private static class DictionaryCalculator {

        private static final int W_SIZE = 1024*32;

        private final byte[] window = new byte[W_SIZE];

        private int windowPos = 0;

        private AtomicLongMap<ByteArrayString> countMap = AtomicLongMap.create();

        public void write(byte[] data, int pos, int end) {
            while (pos < end) {
                byte firstB = data[pos];

                int maxLength = 0;
                int maxWinPos = 0;

                for (int i = ((windowPos - 1) & (W_SIZE - 1)); i != windowPos ; i = (i-1) & (W_SIZE - 1)) {
                    if (firstB == window[i]) {
                        int p1 = pos;
                        int p2 = i;

                        do {
                            p1++;
                            p2 = (p2 + 1) & (W_SIZE - 1);
                        } while (p1 < end && p2 != windowPos && window[p2] == data[p1]);

                        int len = p1 - pos;
                        if (len > maxLength) {
                            maxLength = len;
                            maxWinPos = i;

                        }
                    }
                }

                if (maxLength == 0) {
                    maxLength = 1;
                }

                if (maxLength > 2) {
                    byte[] ddd = new byte[maxLength];
                    for (int i = 0; i < maxLength; i++) {
                        ddd[i] = window[(maxWinPos + i) & (W_SIZE - 1)];
                    }

                    ByteArrayString str = new ByteArrayString(ddd);
                    countMap.incrementAndGet(str);
                }

                for (int i = 0; i < maxLength; i++) {
                    window[windowPos] = data[pos + i];
                    windowPos = (windowPos + 1) & (W_SIZE - 1);
                }

                pos += maxLength;
            }
        }

        public byte[] getDictionary() {
//            Map<ByteArrayString, Long> filteredMap = Maps.filterValues(countMap.asMap(), new Predicate<Long>() {
//                @Override
//                public boolean apply(Long aLong) {
//                    return aLong > 1;
//                }
//            });

            ByteArrayString[] strings = countMap.asMap().keySet().toArray(new ByteArrayString[countMap.size()]);
            Arrays.sort(strings, Ordering.from(new Comparator<ByteArrayString>() {
                @Override
                public int compare(ByteArrayString o1, ByteArrayString o2) {
                    return Long.compare(countMap.get(o1), countMap.get(o2));
                }
            }).reverse());

            int dictionarySize = 0;
            LinkedHashSet<ByteArrayString> usedInDictionaryStrings = new LinkedHashSet<ByteArrayString>();

            for (ByteArrayString s : strings) {
                if (dictionarySize + s.getLength() > 1024 * 4) {
                    break;
                }

                dictionarySize += s.getLength();
                usedInDictionaryStrings.add(s);
            }

            Arrays.sort(strings, Ordering.from(new Comparator<ByteArrayString>() {
                @Override
                public int compare(ByteArrayString o1, ByteArrayString o2) {
                    return Long.compare(o1.getLength() * countMap.get(o1), o2.getLength() * countMap.get(o2));
                }
            }).reverse());

            for (ByteArrayString s : strings) {
                if (!usedInDictionaryStrings.contains(s)) {
                    if (dictionarySize + s.getLength() > 1024 * 27) {
                        break;
                    }

                    dictionarySize += s.getLength();
                    usedInDictionaryStrings.add(s);
                }
            }

            OpenByteOutputStream stream = new OpenByteOutputStream(dictionarySize);

            ByteArrayString[] rrr = usedInDictionaryStrings.toArray(new ByteArrayString[usedInDictionaryStrings.size()]);

            for (int i = rrr.length; --i >= 0; ) {
                try {
                    rrr[i].writeTo(stream);
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }

            byte[] res = stream.getBuffer();

            assert res.length == dictionarySize;
            assert stream.size() == dictionarySize;

            return res;
        }
    }

    public static byte[] buildDictionary(Collection<OpenByteOutputStream> data) {
        DictionaryCalculator dOut = new DictionaryCalculator();

        for (OpenByteOutputStream openByteOutputStream : data) {
            dOut.write(openByteOutputStream.getBuffer(), 0, Math.min(openByteOutputStream.size(), 1024));
        }

        return dOut.getDictionary();
    }
}
