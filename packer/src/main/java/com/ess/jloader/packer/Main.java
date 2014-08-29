package com.ess.jloader.packer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class Main {

    private static final Options options = new Options()
            .addOption("t", "target", true, "Target file.")
            .addOption("rm", "removeSource", false, "Remove source files after packaging.")
            .addOption("d", "directory", true, "Base directory.")
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
                    "java -jar packer.jar -r result.jar source1.jar source2.jar ...\n");
            return;
        }

        DirectoryScanner sc = new DirectoryScanner();
        sc.setIncludes(freeArgs);

        String[] excludes = commandLine.getOptionValues('e');
        if (excludes != null && excludes.length > 0) {
            sc.setExcludes(excludes);
        }

        String basedir = commandLine.getOptionValue('d');
        if (basedir == null) {
            basedir = new File("").getAbsolutePath();
        }

        sc.setBasedir(basedir);

        sc.scan();

        String resultPath = commandLine.getOptionValue('t');

        if (resultPath == null) {
            for (String path : sc.getIncludedFiles()) {
                File file = new File(sc.getBasedir(), path);
                if (file.isFile()) {
                    transformFile(file);
                }
            }
        }
        else {
            File resultFile = new File(resultPath);
            if (!resultFile.isAbsolute()) {
                resultFile = new File(sc.getBasedir(), resultPath);
            }
            if (resultFile.exists()) {
                System.out.println("Target file already exists: " + resultFile);
                return;
            }

            JarPacker packer = new JarPacker(new Config());

            long sourceSize = 0;

            List<File> processedFile = new ArrayList<File>();

            for (String path : sc.getIncludedFiles()) {
                File file = new File(sc.getBasedir(), path);
                if (file.isFile()) {
                    System.out.println("Reading jar: " + path);
                    packer.addJar(file);

                    sourceSize += file.length();
                    processedFile.add(file);
                }
            }

            System.out.println("Creation " + resultFile);

            packer.writeResult(resultFile);

            System.out.printf("Done (%d%%)\n", resultFile.length() * 100 / sourceSize);

            if (commandLine.hasOption("rm")) {
                System.out.println("Deleting source files... ");
                for (File file : processedFile) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        System.out.println("Failed to delete " + file);
                    }
                }
            }
        }
    }

    private static void transformFile(@NotNull File file) throws IOException {
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
