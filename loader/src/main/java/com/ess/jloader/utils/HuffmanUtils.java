package com.ess.jloader.utils;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.SortedMap;

/**
 * @author Sergey Evdokimov
 */
public class HuffmanUtils {

    public static <T> TreeElement buildHuffmanTree(PriorityQueue<TreeElement> queue) {
        while (queue.size() > 1) {
            TreeElement e1 = queue.poll();
            TreeElement e2 = queue.poll();

            queue.add(new Node(e1.weight + e2.weight, e1, e2));
        }

        return queue.peek();
    }

    public static class TreeElement implements Comparable<TreeElement> {
        public final int weight;

        public TreeElement(int weight) {
            this.weight = weight;
        }

        @Override
        public int compareTo(TreeElement o) {
            return weight - o.weight;
        }
    }

    public static class Leaf extends TreeElement {

        public final Object element;

        public Leaf(int weight, Object element) {
            super(weight);
            this.element = element;
        }
    }

    public static class Node extends TreeElement {
        public final TreeElement trueNode;
        public final TreeElement falseNode;

        public Node(int weight, TreeElement trueNode, TreeElement falseNode) {
            super(weight);
            this.trueNode = trueNode;
            this.falseNode = falseNode;
        }
    }

}
