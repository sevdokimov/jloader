import com.google.common.base.Throwables;
import ru.testData.guava.ITest;
import ru.testData.guava.TestLists;
import ru.testData.guava.TestMaps;
import ru.testData.guava.TestPredicates;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Sergey Evdokimov
 */
public class Main {

    private static final Pattern CLASS_PATTERN = Pattern.compile("(.*)\\.c(?:lass)?");

    public static File getJarByMarker(String marker) {
        String path = Thread.currentThread().getContextClassLoader().getResource(marker).getPath();
        int idx = path.lastIndexOf("!/");
        assert idx > 0;

        path = path.substring(0, idx);
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }

        return new File(path);
    }

    private static void loadAll(String marker) throws IOException, ClassNotFoundException {
        File jarByMarker = getJarByMarker(marker);

        List<String> classes = new ArrayList<String>();

        ZipInputStream zIn = new ZipInputStream(new BufferedInputStream(new FileInputStream(jarByMarker)));
        try {
            ZipEntry entry;
            while ( (entry = zIn.getNextEntry()) != null) {
                String name = entry.getName();

                Matcher matcher = CLASS_PATTERN.matcher(name);

                if (matcher.matches()) {
                    String className = matcher.group(1).replace('/', '.');
                    classes.add(className);
                }
            }
        } finally {
            zIn.close();
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String aClass : classes) {
            loader.loadClass(aClass);
        }

        System.out.println("Loaded classes: " + classes.size());
    }

    private static void runTests() throws Exception {
        Class<ITest>[] testClasses = new Class[]{TestMaps.class, TestPredicates.class, TestLists.class};

        for (Class<ITest> aClass : testClasses) {
            ITest instance = aClass.newInstance();

            for (Method method : aClass.getMethods()) {
                if (method.getName().startsWith("test") && method.getParameterTypes().length == 0) {
                    method.invoke(instance, new Object[0]);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        long time = System.currentTimeMillis();

        runTests();
        loadAll("META-INF/maven/com.google.guava/guava/pom.properties");

        System.out.println("Loading time: " + (System.currentTimeMillis() - time));
    }

}
