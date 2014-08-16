package com.ess.jloader.utils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sergey Evdokimov
 */
public class Utils {

    public static final String[] PREDEFINED_UTF = {
            "Code",
            "LineNumberTable",
            "LocalVariableTable",
            "Exceptions",
            "InnerClasses",
            "Synthetic",
            "Signature",
    };



    public static final byte[] PREDEFINED_UTF_BYTES;
    public static final int[] PREDEFINED_UTF_BYTE_INDEXES;

    public static final byte[] C_SourceFile = toByteArray("SourceFile");

    static {
        try {
            // Init predefined bytes
            PREDEFINED_UTF_BYTE_INDEXES = new int[PREDEFINED_UTF.length + 1];

            int bytesCount = 0;
            for (int i = 0; i < PREDEFINED_UTF.length; i++) {
                PREDEFINED_UTF_BYTE_INDEXES[i] = bytesCount;

                bytesCount += 2 + PREDEFINED_UTF[i].length();
            }

            PREDEFINED_UTF_BYTE_INDEXES[PREDEFINED_UTF.length] = bytesCount;

            PREDEFINED_UTF_BYTES = new byte[bytesCount];
            DataOutputStream out = new DataOutputStream(new OpenByteOutputStream(PREDEFINED_UTF_BYTES));
            for (String s : PREDEFINED_UTF) {
                out.writeUTF(s);
            }

            assert out.size() == bytesCount;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static final int PS_CODE = 0;
    public static final int PS_EXCEPTIONS = 3;
    public static final int PS_INNER_CLASSES = 4;
    public static final int PS_SIGNATURE = 6;

    public static final byte PACKER_VERSION = 0x01;

    public static final byte MAGIC = (byte) 0xAA;

    public static String fileNameToClassName(String fileName) {
        assert fileName.endsWith(".class") : fileName;
        return fileName.substring(0, fileName.length() - ".class".length());
    }

    public static String generateSourceFileName(String className) {
        className = className.substring(className.lastIndexOf('/') + 1);

        int dollarIndex = className.indexOf('$');
        if (dollarIndex != -1) {
            className = className.substring(0, dollarIndex);
        }

        return className.concat(".java");
    }

    public static void read(DataInputStream in, ByteBuffer buffer, int length) throws IOException {
        int position = buffer.position();
        in.readFully(buffer.array(), position, length);
        buffer.position(position + length);
    }

    public static byte[] toByteArray(String s) {
        try {
            int size = s.length() + 2;
            OpenByteOutputStream byteOut = new OpenByteOutputStream(size);
            new DataOutputStream(byteOut).writeUTF(s);

            assert byteOut.size() == size;

            return byteOut.getBuffer();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
