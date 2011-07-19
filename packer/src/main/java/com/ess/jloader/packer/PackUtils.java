package com.ess.jloader.packer;

import com.ess.jloader.packer.attribute.AttributeParser;
import com.ess.jloader.packer.consts.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class PackUtils {

    public static final Pattern TYPE_PATTERN = Pattern.compile("(\\[*)(?:([BCDFIJSZ])|L(([\\w\\$]+)(/[\\w\\$]+)*);)");

    public static final Pattern CLASS_NAME_PATTERN = Pattern.compile("[\\w\\$]+(/[\\w\\$]+)*");

    private PackUtils() {

    }

    private static class Package {
        private List<String> classes = new ArrayList<String>();

        private Map<String, Package> packages = new TreeMap<String, Package>();
    }

    public static Map<String, Integer> getTypeUsages(Collection<AClass> classes) throws InvalidClassException {
        Map<String, Integer> typeUsages = new HashMap<String, Integer>();

        Set<ConstUtf> types = new HashSet<ConstUtf>();

        for (AClass aClass : classes) {
            List<? extends Const> consts = aClass.getConsts();

            types.clear();

            for (Const c : consts) {
                if (c instanceof ConstRef) {
                    types.add(((ConstRef) c).getNameAndType().get().getType().get());
                }
            }

            for (ConstUtf type : types) {
                String text = type.getText();

                if (text.startsWith("(")) {
                    int closedBracketIndex = text.lastIndexOf(')');
                    if (closedBracketIndex == -1) throw new InvalidClassException();

                    String returnType = text.substring(closedBracketIndex + 1);
                    if (!returnType.equals("V")) {
                        assertType(returnType);
                        inc(typeUsages, returnType);
                    }

                    if (closedBracketIndex > 1) {
                        String params = text.substring(1, closedBracketIndex);
                        Matcher m = TYPE_PATTERN.matcher(params);
                        int idx = 0;
                        while (m.find()) {
                            if (m.start() != idx) {
                                throw new InvalidClassException(text);
                            }

                            inc(typeUsages, m.group());
                            idx = m.end();
                        }

                        if (idx != params.length()) throw new InvalidClassException(text);
                    }
                }
                else {
                    assertType(text);
                    inc(typeUsages, text);
                }
            }
        }

        return typeUsages;
    }
    public static Map<String, Integer> getClassNameUsages(Collection<AClass> classes) throws InvalidClassException {
        Map<String, Integer> classUsages = new HashMap<String, Integer>();

        for (AClass aClass : classes) {
            List<? extends Const> consts = aClass.getConsts();

            Set<ConstUtf> classNames = new HashSet<ConstUtf>();

            for (Const c : consts) {
                if (c instanceof ConstClass) {
                    classNames.add(((ConstClass) c).getName().get());
                }
            }

            for (ConstUtf className : classNames) {
                String text = className.getText();
                if (!text.startsWith("[")) {
                    inc(classUsages, text);
                }
            }
        }

        return classUsages;
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

    public static void assertType(String s) throws InvalidClassException {
        if (!TYPE_PATTERN.matcher(s).matches()) {
            throw new InvalidClassException();
        }
    }

    public static Map<String, Integer> writeClassNames(Collection<String> classNames, DataOutput out) throws IOException {
        assertSorted(classNames);

        Package root = new Package();

        for (String className : classNames) {
            if (!CLASS_NAME_PATTERN.matcher(className).matches()) throw new InvalidClassException(className);

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

    public static Map<String, Object> readAttrs(AClass aClass, DataInput in, Map<String, AttributeParser> parsers) throws IOException {
        int attrCount = in.readUnsignedShort();

        Map<String, Object> res = new HashMap<String, Object>();

        for (int i = 0; i < attrCount; i++) {
            String name = aClass.createRef(ConstUtf.class, in).get().getText();

            int length = in.readInt();

            AttributeParser parser = parsers.get(name);
            if (parser == null) {
                throw new InvalidClassException("Uncknown attribute: " + name);
            }

            res.put(name, parser.parse(aClass, length, in));
        }

        return res;
    }

}
