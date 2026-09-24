package com.minju.geogdalservice.routing;

import com.minju.geogdalservice.gis.GridIndex;
import com.minju.geogdalservice.network.RoadLinkData;
import org.locationtech.jts.geom.Coordinate;

import java.util.Arrays;
import java.util.List;

/**
 * 경로탐색용 불변 도로 그래프 (CSR: Compressed Sparse Row).
 *
 * 노드 u 에서 나가는 간선은 edgeTarget[edgeOffset[u] .. edgeOffset[u+1]) 에 연속으로 저장된다.
 * 객체 그래프(Node/Edge 객체 + List) 대비 메모리가 작고 캐시 지역성이 좋아 탐색이 빠르다.
 *
 * 링크 하나당 정방향 간선과 역방향 간선을 모두 만들고, 일방통행의 역방향 간선은
 * 차량 통행 불가(carAllowed=false)로 표시한다. (보행/자전거는 일방통행 무관)
 *
 * 한 번 만들어진 그래프는 변경되지 않으므로 여러 요청 스레드가 잠금 없이 동시에 탐색할 수 있다.
 */
public final class RoadGraph {

    private static final double INDEX_CELL_DEG = 0.002;   // 약 200m

    private final long versionId;
    private final String datasetName;
    private final int versionNo;

    // 노드
    private final double[] lon;
    private final double[] lat;
    private final double[] elevation;   // DEM 이 없으면 NaN

    // 간선 (CSR)
    private final int[] edgeOffset;
    private final int[] edgeSource;
    private final int[] edgeTarget;
    private final int[] edgeLink;
    private final boolean[] edgeForward;
    private final boolean[] edgeCarAllowed;

    // 링크 속성
    private final double[] linkLength;
    private final int[] linkMaxSpeed;
    private final String[] linkRoadClass;
    private final String[] linkName;
    private final String[] linkSourceId;
    private final double[][] linkCoords;   // [lon0, lat0, lon1, lat1, ...]

    private final double maxCarSpeedKph;
    private final GridIndex nodeIndex;

    private RoadGraph(Builder b) {
        this.versionId = b.versionId;
        this.datasetName = b.datasetName;
        this.versionNo = b.versionNo;

        int n = b.nodes.size();
        this.lon = new double[n];
        this.lat = new double[n];
        for (int i = 0; i < n; i++) {
            lon[i] = b.nodes.get(i).x;
            lat[i] = b.nodes.get(i).y;
        }
        this.elevation = b.elevations != null ? b.elevations.clone() : filledNaN(n);

        int linkCount = b.links.size();
        linkLength = new double[linkCount];
        linkMaxSpeed = new int[linkCount];
        linkRoadClass = new String[linkCount];
        linkName = new String[linkCount];
        linkSourceId = new String[linkCount];
        linkCoords = new double[linkCount][];
        double maxSpeed = 1;
        for (int l = 0; l < linkCount; l++) {
            RoadLinkData link = b.links.get(l);
            linkLength[l] = link.lengthM();
            linkMaxSpeed[l] = link.maxSpeedKph() == null ? 0 : link.maxSpeedKph();
            linkRoadClass[l] = link.roadClass();
            linkName[l] = link.name();
            linkSourceId[l] = link.sourceId();
            Coordinate[] cs = link.geometry().getCoordinates();
            double[] flat = new double[cs.length * 2];
            for (int i = 0; i < cs.length; i++) {
                flat[i * 2] = cs[i].x;
                flat[i * 2 + 1] = cs[i].y;
            }
            linkCoords[l] = flat;
            if (RoadRules.carAllowed(link.roadClass())) {
                maxSpeed = Math.max(maxSpeed, RoadRules.carSpeedKph(link.roadClass(), linkMaxSpeed[l]));
            }
        }
        this.maxCarSpeedKph = maxSpeed;

        // 1) 노드별 나가는 간선 수 집계 -> 2) 누적합으로 오프셋 -> 3) 채우기
        int edgeCount = linkCount * 2;
        edgeOffset = new int[n + 1];
        for (RoadLinkData link : b.links) {
            edgeOffset[link.fromNode() + 1]++;
            edgeOffset[link.toNode() + 1]++;
        }
        for (int i = 0; i < n; i++) {
            edgeOffset[i + 1] += edgeOffset[i];
        }
        edgeSource = new int[edgeCount];
        edgeTarget = new int[edgeCount];
        edgeLink = new int[edgeCount];
        edgeForward = new boolean[edgeCount];
        edgeCarAllowed = new boolean[edgeCount];
        int[] cursor = Arrays.copyOf(edgeOffset, n);
        for (int l = 0; l < linkCount; l++) {
            RoadLinkData link = b.links.get(l);
            boolean car = RoadRules.carAllowed(link.roadClass());
            put(cursor[link.fromNode()]++, link.fromNode(), link.toNode(), l, true, car);
            put(cursor[link.toNode()]++, link.toNode(), link.fromNode(), l, false, car && !link.oneway());
        }

        this.nodeIndex = GridIndex.build(lon, lat, INDEX_CELL_DEG);
    }

    private void put(int e, int from, int to, int link, boolean forward, boolean carAllowed) {
        edgeSource[e] = from;
        edgeTarget[e] = to;
        edgeLink[e] = link;
        edgeForward[e] = forward;
        edgeCarAllowed[e] = carAllowed;
    }

    private static double[] filledNaN(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- 조회 ----

    public int nodeCount() {
        return lon.length;
    }

    public int edgeCount() {
        return edgeTarget.length;
    }

    public int linkCount() {
        return linkLength.length;
    }

    public int edgeStart(int node) {
        return edgeOffset[node];
    }

    public int edgeEnd(int node) {
        return edgeOffset[node + 1];
    }

    public int edgeSource(int e) {
        return edgeSource[e];
    }

    public int edgeTarget(int e) {
        return edgeTarget[e];
    }

    public int edgeLink(int e) {
        return edgeLink[e];
    }

    public boolean edgeForward(int e) {
        return edgeForward[e];
    }

    public boolean edgeCarAllowed(int e) {
        return edgeCarAllowed[e];
    }

    public double edgeLength(int e) {
        return linkLength[edgeLink[e]];
    }

    public String edgeRoadClass(int e) {
        return linkRoadClass[edgeLink[e]];
    }

    public int edgeMaxSpeed(int e) {
        return linkMaxSpeed[edgeLink[e]];
    }

    /**
     * 진행 방향 경사도 (상승 +). 고도 정보가 없으면 0
     */
    public double edgeSlope(int e) {
        double from = elevation[edgeSource[e]];
        double to = elevation[edgeTarget[e]];
        double length = edgeLength(e);
        if (Double.isNaN(from) || Double.isNaN(to) || length <= 0) {
            return 0;
        }
        return Math.max(-1, Math.min(1, (to - from) / length));
    }

    public double lon(int node) {
        return lon[node];
    }

    public double lat(int node) {
        return lat[node];
    }

    public double elevation(int node) {
        return elevation[node];
    }

    public boolean hasElevation() {
        for (double e : elevation) {
            if (!Double.isNaN(e)) {
                return true;
            }
        }
        return false;
    }

    public String linkName(int link) {
        return linkName[link];
    }

    public String linkRoadClass(int link) {
        return linkRoadClass[link];
    }

    public String linkSourceId(int link) {
        return linkSourceId[link];
    }

    public double[] linkCoords(int link) {
        return linkCoords[link];
    }

    public double maxCarSpeedKph() {
        return maxCarSpeedKph;
    }

    /**
     * 좌표에서 가장 가까운 노드 (maxDistanceM 안에 없으면 -1)
     */
    public int nearestNode(double lon, double lat, double maxDistanceM) {
        return nodeIndex.nearest(lon, lat, maxDistanceM);
    }

    public long versionId() {
        return versionId;
    }

    public String datasetName() {
        return datasetName;
    }

    public int versionNo() {
        return versionNo;
    }

    public static final class Builder {
        private long versionId;
        private String datasetName;
        private int versionNo;
        private List<Coordinate> nodes = List.of();
        private List<RoadLinkData> links = List.of();
        private double[] elevations;

        public Builder version(long versionId, String datasetName, int versionNo) {
            this.versionId = versionId;
            this.datasetName = datasetName;
            this.versionNo = versionNo;
            return this;
        }

        public Builder nodes(List<Coordinate> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder links(List<RoadLinkData> links) {
            this.links = links;
            return this;
        }

        public Builder elevations(double[] elevations) {
            this.elevations = elevations;
            return this;
        }

        public RoadGraph build() {
            if (elevations != null && elevations.length != nodes.size()) {
                throw new IllegalArgumentException("elevations 길이가 노드 수와 다릅니다.");
            }
            for (RoadLinkData link : links) {
                if (link.fromNode() < 0 || link.fromNode() >= nodes.size()
                        || link.toNode() < 0 || link.toNode() >= nodes.size()) {
                    throw new IllegalArgumentException("링크가 존재하지 않는 노드를 참조합니다.");
                }
            }
            return new RoadGraph(this);
        }
    }
}
