package com.ess.jloader.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings({"unchecked"})
public class HuffmanOutputStream {

    private final Map<Object, boolean[]> pathMap;

    private OutputStream out;

    private int bits;
    private int bitCount;

    public HuffmanOutputStream(HuffmanUtils.TreeElement root) {
        Map<Object, boolean[]> pathMap = new HashMap<Object, boolean[]>();
        buildHuffmanMap(pathMap, new ArrayList<Boolean>(), root);
        this.pathMap = pathMap;
    }

    public void reset(OutputStream out) {
        this.out = out;
        bitCount = 0;
    }

    public void write(Object t) throws IOException {
        boolean[] booleans = pathMap.get(t);
        for (int i = booleans.length; --i >= 0; ) {
            bits <<= 1;
            if (booleans[i]) {
                bits++;
            }

            if (++bitCount == 8) {
                out.write(bits);
                bits = 0;
                bitCount = 0;
            }
        }
    }

    public void finish() throws IOException {
        if (bitCount > 0) {
            out.write(bits);
        }
    }

    public static HuffmanOutputStream create(SortedMap<? extends Object, Integer> map) {
        HuffmanUtils.TreeElement root = HuffmanUtils.buildHuffmanTree(map);
        return new HuffmanOutputStream(root);
    }

    private static <T> void buildHuffmanMap(Map<T, boolean[]> res, List<Boolean> path, HuffmanUtils.TreeElement element) {
        if (element instanceof HuffmanUtils.Leaf) {
            boolean[] pathArray = new boolean[path.size()];
            for (int i = 0; i < pathArray.length; i++) {
                pathArray[i] = path.get(i);
            }

            res.put((T)((HuffmanUtils.Leaf) element).element, pathArray);
        }
        else if (element instanceof HuffmanUtils.Node) {
            HuffmanUtils.Node node = (HuffmanUtils.Node) element;

            path.add(true);
            buildHuffmanMap(res, path, node.trueNode);
            path.set(path.size() - 1, false);
            buildHuffmanMap(res, path, node.falseNode);
            path.remove(path.size() - 1);
        }
        else {
            assert false;
        }
    }

}
