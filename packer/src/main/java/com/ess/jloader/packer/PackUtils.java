package com.ess.jloader.packer;

import com.ess.jloader.packer.consts.*;

import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class PackUtils {

    public static final Pattern TYPE_PATTERN = Pattern.compile("(\\[*)(?:([BCDFIJSZ])|L(([\\w_]+)(/[\\w_]+)*);)");

    private PackUtils() {

    }

    private static class Package {
        private List<String> classes = new ArrayList<String>();

        private Map<String, Package> packages = new TreeMap<String, Package>();
    }

    public static Map<String, Integer> extractTypes(Collection<AClass> classes) throws InvalidClassException {
        Map<String, Integer> res = new HashMap<String, Integer>();

        for (AClass aClass : classes) {
            List<Const> consts = aClass.getConsts();

            boolean[] f = new boolean[consts.size()];

            for (Const c : consts) {
                if (c instanceof ConstClass) {
                    f[ ((ConstClass) c).getTypeIndex() ] = true;
                }
                else if (c instanceof ConstRef) {
                    ConstClass constClass = (ConstClass) consts.get(((ConstRef) c).getClassIndex());
                    f[ constClass.getTypeIndex() ] = true;

                    ConstNameAndType nt = (ConstNameAndType) consts.get(((ConstRef) c).getNameAndTypeIndex());
                    f[nt.getTypeIndex()] = true;
                }
            }

            for (int i = 0; i < f.length; i++) {
                if (!f[i]) continue;

                ConstUTF8 utf = (ConstUTF8) consts.get(i);
                String text = utf.getText();

                if (text.startsWith("(")) {
                    int closedBracketIndex = text.lastIndexOf(')');
                    if (closedBracketIndex == -1) throw new InvalidClassException();

                    String returnType = text.substring(closedBracketIndex + 1);
                    if (!returnType.equals("V")) {
                        assertType(returnType);
                        inc(res, returnType);
                    }

                    if (closedBracketIndex > 1) {
                        String params = text.substring(1, closedBracketIndex);
                        Matcher m = TYPE_PATTERN.matcher(params);
                        int idx = 0;
                        while (m.find()) {
                            if (m.start() != idx) {
                                throw new InvalidClassException(text);
                            }

                            inc(res, m.group());
                            idx = m.end();
                        }

                        if (idx != params.length()) throw new InvalidClassException(text);
                    }
                }
                else {
                    assertType(text);
                    inc(res, text);
                }
            }
        }

        res.remove("V");

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

    public static void assertType(String s) throws InvalidClassException {
        if (!TYPE_PATTERN.matcher(s).matches()) {
            throw new InvalidClassException();
        }
    }

    public static Map<String, Integer> writeClassNames(Collection<String> classNames, DataOutput out) throws IOException {
        StringBuilder sb = new StringBuilder();

        Package root = new Package();

        for (String className : classNames) {
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

        writePackages(sb, root);
        writeClasses(sb, root);

        return null;
    }

    private static void writePackages(StringBuilder sb, Package p) {
        for (Map.Entry<String, Package> entry : p.packages.entrySet()) {
            sb.append(entry.getKey());
            sb.append(',');
            writePackages(sb, entry.getValue());
        }

        sb.append(',');
    }

    private static void writeClasses(StringBuilder sb, Package p) {
        Collections.sort(p.classes);

        for (String className : p.classes) {
            sb.append(className);
            sb.append(',');
        }

        sb.append(',');

        for (Package subPackage : p.packages.values()) {
            writeClasses(sb, subPackage);
        }
    }
}
