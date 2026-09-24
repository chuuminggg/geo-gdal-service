package com.minju.geogdalservice.network;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class UnionFindTest {

    @Test
    void 합치기와_연결_요소_수() {
        UnionFind uf = new UnionFind(6);
        assertThat(uf.componentCount()).isEqualTo(6);

        assertThat(uf.union(0, 1)).isTrue();
        assertThat(uf.union(1, 2)).isTrue();
        assertThat(uf.union(0, 2)).isFalse();   // 이미 같은 집합
        uf.union(3, 4);

        assertThat(uf.componentCount()).isEqualTo(3);   // {0,1,2}, {3,4}, {5}
        assertThat(uf.find(2)).isEqualTo(uf.find(0));
        assertThat(uf.componentSize(4)).isEqualTo(2);
        assertThat(uf.largestComponentSize()).isEqualTo(3);
    }

    @Test
    void 긴_체인도_경로_압축으로_처리된다() {
        int n = 200_000;
        UnionFind uf = new UnionFind(n);
        for (int i = 1; i < n; i++) {
            uf.union(i - 1, i);
        }
        assertThat(uf.componentCount()).isEqualTo(1);
        assertThat(uf.largestComponentSize()).isEqualTo(n);

        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertThat(uf.find(random.nextInt(n))).isEqualTo(uf.find(0));
        }
    }
}
