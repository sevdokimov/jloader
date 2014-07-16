package com.ess.jloader.utils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Sergey Evdokimov
 */
public class HuffmanInputStream<T> {

    private final InputStream inputStream;

    private final HuffmanUtils.TreeElement root;

    private int b;
    private int bitCount;

    public HuffmanInputStream(InputStream inputStream, HuffmanUtils.TreeElement root) {
        this.inputStream = inputStream;
        this.root = root;
    }

    public T read() throws IOException {
        HuffmanUtils.TreeElement p = root;

        while (p instanceof HuffmanUtils.Node) {
            if (bitCount == 0) {
                b = inputStream.read();
                if (b == -1) throw new EOFException();

                bitCount = 8;
            }

            if ((b & 1) > 0) {
                p = ((HuffmanUtils.Node) p).trueNode;
            }
            else {
                p = ((HuffmanUtils.Node) p).falseNode;
            }
            bitCount--;
            b >>>= 1;
        }

        return (T) ((HuffmanUtils.Leaf)p).element;
    }
}
