package com.minju.geogdalservice.routing;

import java.util.Arrays;

/**
 * (double 우선순위, int 값) 이진 최소 힙.
 * java.util.PriorityQueue 는 원소마다 객체를 만들고 박싱하므로, 대규모 그래프 탐색에서
 * GC 부담을 줄이기 위해 원시 배열 기반으로 구현했다. 삽입/삭제 O(log n).
 *
 * decrease-key 대신 같은 노드를 새 우선순위로 다시 넣고(lazy deletion),
 * 꺼낼 때 이미 확정된 노드는 건너뛰는 방식으로 사용한다.
 */
final class MinHeap {

    private double[] keys;
    private int[] values;
    private int size;

    MinHeap(int initialCapacity) {
        keys = new double[Math.max(4, initialCapacity)];
        values = new int[keys.length];
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void push(double key, int value) {
        if (size == keys.length) {
            keys = Arrays.copyOf(keys, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        int i = size++;
        // sift up
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (keys[parent] <= key) {
                break;
            }
            keys[i] = keys[parent];
            values[i] = values[parent];
            i = parent;
        }
        keys[i] = key;
        values[i] = value;
    }

    double peekKey() {
        return keys[0];
    }

    int pop() {
        int top = values[0];
        size--;
        if (size > 0) {
            double key = keys[size];
            int value = values[size];
            int i = 0;
            // sift down
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && keys[child + 1] < keys[child]) {
                    child++;
                }
                if (keys[child] >= key) {
                    break;
                }
                keys[i] = keys[child];
                values[i] = values[child];
                i = child;
            }
            keys[i] = key;
            values[i] = value;
        }
        return top;
    }
}
