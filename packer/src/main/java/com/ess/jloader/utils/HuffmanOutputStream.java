package com.ess.jloader.utils;

import com.google.common.collect.Maps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * @author Sergey Evdokimov
 */
@SuppressWarnings({"unchecked"})
public class HuffmanOutputStream<T> {

    private final Map<T, boolean[]> pathMap;

    private BitOutputStream out;

    public HuffmanOutputStream(Map<T, boolean[]> pathMap) {
        this.pathMap = pathMap;
    }

    public void reset(BitOutputStream out) {
        this.out = out;
    }

    public void write(T t) throws IOException {
        boolean[] booleans = pathMap.get(t);
        for (boolean aBoolean : booleans) {
            out.writeBit(aBoolean);
        }
    }

    private static <T> void buildPathMap(Map<T, boolean[]> res, List<Boolean> path, HuffmanUtils.TreeElement element) {
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
            buildPathMap(res, path, node.trueNode);
            path.set(path.size() - 1, false);
            buildPathMap(res, path, node.falseNode);
            path.remove(path.size() - 1);
        }
        else {
            assert false;
        }
    }

    public static <T> Map<T, boolean[]> buildPathMap(Map<T, Integer> map) {
        assert map.size() > 0;

        PriorityQueue<HuffmanUtils.TreeElement> queue = new PriorityQueue<HuffmanUtils.TreeElement>();

        for (Map.Entry<T, Integer> entry : map.entrySet()) {
            queue.add(new HuffmanUtils.Leaf(entry.getValue(), entry.getKey()));
        }

        HuffmanUtils.TreeElement root = HuffmanUtils.buildHuffmanTree(queue);

        Map<T, boolean[]> res = Maps.newHashMapWithExpectedSize(map.size());

        buildPathMap(res, new ArrayList<Boolean>(), root);

        return res;
    }

}
