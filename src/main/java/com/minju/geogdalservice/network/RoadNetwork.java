package com.minju.geogdalservice.network;

import org.locationtech.jts.geom.Coordinate;

import java.util.List;

/**
 * @param nodes           노드 좌표 (인덱스 = node_seq)
 * @param links           링크
 * @param skippedFeatures 라인이 아니거나 잘못된 geometry 라서 제외된 피처 수
 */
public record RoadNetwork(List<Coordinate> nodes, List<RoadLinkData> links, int skippedFeatures) {

    public Connectivity connectivity() {
        UnionFind uf = new UnionFind(nodes.size());
        for (RoadLinkData link : links) {
            uf.union(link.fromNode(), link.toNode());
        }
        return new Connectivity(uf.componentCount(), uf.largestComponentSize(), nodes.size());
    }

    public record Connectivity(int componentCount, int largestComponentSize, int nodeCount) {
        public double largestComponentRatio() {
            return nodeCount == 0 ? 0 : (double) largestComponentSize / nodeCount;
        }
    }
}
