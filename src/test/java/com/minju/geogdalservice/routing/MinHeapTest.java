package com.minju.geogdalservice.routing;

import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MinHeapTest {

    @Test
    void 삽입과_삭제를_섞어도_항상_최소값부터_나온다() {
        Random random = new Random(3);
        MinHeap heap = new MinHeap(4);
        PriorityQueue<double[]> reference = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));

        for (int i = 0; i < 50_000; i++) {
            if (heap.isEmpty() || random.nextInt(3) > 0) {
                double key = random.nextDouble() * 1000;
                heap.push(key, i);
                reference.add(new double[]{key, i});
            } else {
                double expected = reference.poll()[0];
                assertThat(heap.peekKey()).isEqualTo(expected);
                heap.pop();
            }
            assertThat(heap.size()).isEqualTo(reference.size());
        }
        double previous = -1;
        while (!heap.isEmpty()) {
            double key = heap.peekKey();
            heap.pop();
            assertThat(key).isGreaterThanOrEqualTo(previous);
            previous = key;
        }
    }
}
