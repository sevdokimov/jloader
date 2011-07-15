package com.ess.jloader.loader;

/**
 * @author Sergey Evdokimov
 */
public class Utils {

    public static String fileNameToClassName(String fileName) {
        assert fileName.endsWith(".class");
        return fileName.substring(0, fileName.length() - ".class".length()).replace('/', '.');
    }

}
