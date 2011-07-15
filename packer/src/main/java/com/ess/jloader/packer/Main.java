package com.ess.jloader.packer;

import sun.text.normalizer.IntTrie;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class Main {

    public static void main(String[] args) throws IOException {
        JarPacker.pack(new File(args[0]), new File(args[1]));
    }

}
