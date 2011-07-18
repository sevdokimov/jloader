package com.ess.jloader.utils;

/**
 * @author Sergey Evdokimov
 */
public class Utils {

    public static final short MAGIC = ('J' << 8) + 'A';

    public static String fileNameToClassName(String fileName) {
        assert fileName.endsWith(".class");
        return fileName.substring(0, fileName.length() - ".class".length()).replace('/', '.');
    }

}
