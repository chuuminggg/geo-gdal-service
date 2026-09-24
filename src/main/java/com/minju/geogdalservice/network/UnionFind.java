package com.minju.geogdalservice.network;

/**
 * Disjoint Set (Union-Find) - 경로 압축 + 랭크 기반 합치기.
 * 도로 네트워크의 연결 요소(끊어진 도로망) 분석에 사용한다. 연산당 거의 O(1) (역 아커만 함수).
 */
public class UnionFind {

    private final int[] parent;
    private final byte[] rank;
    private final int[] size;
    private int components;

    public UnionFind(int n) {
        parent = new int[n];
        rank = new byte[n];
        size = new int[n];
        components = n;
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
    }

    public int find(int x) {
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        // 경로 압축: 탐색한 노드들을 루트에 바로 연결
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    public boolean union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) {
            return false;
        }
        if (rank[ra] < rank[rb]) {
            int t = ra;
            ra = rb;
            rb = t;
        }
        parent[rb] = ra;
        size[ra] += size[rb];
        if (rank[ra] == rank[rb]) {
            rank[ra]++;
        }
        components--;
        return true;
    }

    public int componentCount() {
        return components;
    }

    public int componentSize(int x) {
        return size[find(x)];
    }

    public int largestComponentSize() {
        int max = 0;
        for (int i = 0; i < parent.length; i++) {
            if (parent[i] == i) {
                max = Math.max(max, size[i]);
            }
        }
        return max;
    }
}
