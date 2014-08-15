package com.ess.jloader.utils;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class HuffmanInputStream<T> {

    private final BitInputStream inputStream;

    private final HuffmanUtils.TreeElement root;

    public HuffmanInputStream(BitInputStream inputStream, HuffmanUtils.TreeElement root) {
        this.inputStream = inputStream;
        this.root = root;
    }

    public T read() throws IOException {
        HuffmanUtils.TreeElement p = root;

        while (p instanceof HuffmanUtils.Node) {
            if (inputStream.readBits(1) > 0) {
                p = ((HuffmanUtils.Node) p).trueNode;
            }
            else {
                p = ((HuffmanUtils.Node) p).falseNode;
            }
        }

        return (T) ((HuffmanUtils.Leaf)p).element;
    }
}
