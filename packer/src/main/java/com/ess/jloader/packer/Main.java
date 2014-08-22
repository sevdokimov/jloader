package com.ess.jloader.packer;

import com.google.common.base.Predicate;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class Main {

    private static final Options options = new Options()
            .addOption("e", "exclude", true, "Exclude files from transformation.");

    public static void main(String[] args) throws IOException {
        CommandLine commandLine;

        try {
            commandLine = new GnuParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println("Failed to parse command line: " + e.getMessage());
            return;
        }

        String[] freeArgs = commandLine.getArgs();
        if (freeArgs.length == 0) {
            System.out.println("Usages:\n" +
                    "java -jar packer.jar result.jar source1.jar source2.jar ...\n" +
                    "java -jar transformedArchive.jar\n" +
                    "java -jar directoryToTransform");
            return;
        }

        if (freeArgs.length > 1) {
            File result = new File(freeArgs[0]);

            JarPacker packer = new JarPacker(new Config());

            for (int i = 1; i < freeArgs.length; i++) {
                File jarFile = new File(freeArgs[i]);

                if (!jarFile.isFile()) {
                    System.out.println("Failed to read source jar file: " + jarFile);
                    return;
                }

                packer.addJar(jarFile);
            }

            packer.writeResult(result);
            return;
        }

        File file = new File(freeArgs[0]);

        if (!file.exists()) {
            System.out.printf("File %s is not exists.", file);
        }

        final String[] excludes = commandLine.getOptionValues('e');

        transform(file, new Predicate<File>() {
            Set<String> excludedFiles = new HashSet<String>(excludes == null ? Collections.<String>emptyList() : Arrays.<String>asList(excludes));
            @Override
            public boolean apply(File file) {
                return excludedFiles.contains(file.getName());
            }
        });
    }

    private static void transform(@NotNull File file, Predicate<File> excludeFiles) throws IOException {
        if (file.isFile()) {
            if (file.getName().endsWith(".jar")) {
                if (excludeFiles.apply(file)) {
                    System.out.println("Skip " + file);
                    return;
                }

                System.out.printf("Packing %s ...", file.getPath());

                if (PackUtils.isPackedJar(file)) {
                    System.out.println(" already packed");
                    return;
                }

                long size = file.length();

                JarPacker packer = new JarPacker(new Config());
                packer.addJar(file);
                if (packer.hasClasses()) {
                    packer.writeResult(file);

                    packer.checkResult(file);

                    System.out.printf(" done (%d%%)\n", file.length() * 100 / size);
                }
                else {
                    System.out.println(" has no classes");
                }
            }
        }
        else if (file.isDirectory()) {
            File[] listFiles = file.listFiles();
            if (listFiles != null) {
                for (File f : listFiles) {
                    transform(f, excludeFiles);
                }
            }
        }
    }

}
