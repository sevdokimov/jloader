package com.ess.jloader.utils;

/**
 * @author Sergey Evdokimov
 */
public class Utils {

    public static final byte PACKER_VERSION = 0x01;

    public static final byte MAGIC = (byte) 0xAA;

    /*
     * Flag bits:
     * 0 - F_LONG_CLASS
     *
     */


    public static final int F_LONG_CLASS = 1;


    public static String fileNameToClassName(String fileName) {
        assert fileName.endsWith(".class") : fileName;
        return fileName.substring(0, fileName.length() - ".class".length()).replace('/', '.');
    }

}
