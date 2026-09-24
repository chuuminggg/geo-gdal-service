package com.minju.geogdalservice.routing;

/**
 * 이동 수단별 비용(소요 시간, 초) 모델.
 *
 * A* 휴리스틱이 최적해를 보장하려면(admissible) 실제 비용을 과대평가하면 안 되므로,
 * 각 수단이 낼 수 있는 최대 속도로 직선거리를 이동하는 시간을 하한으로 사용한다.
 */
public enum TravelMode {

    CAR {
        @Override
        public double cost(RoadGraph g, int e) {
            if (!g.edgeCarAllowed(e)) {
                return Double.POSITIVE_INFINITY;
            }
            return g.edgeLength(e) / kphToMps(RoadRules.carSpeedKph(g.edgeRoadClass(e), g.edgeMaxSpeed(e)));
        }

        @Override
        public double maxSpeedMps(RoadGraph g) {
            return kphToMps(g.maxCarSpeedKph());
        }
    },

    /**
     * 보행: Tobler 보행 함수 v = 6 * e^(-3.5 * |slope + 0.05|) km/h
     * 약간의 내리막(-5%)에서 최대 6km/h, 오르막/급경사에서 느려진다.
     */
    WALK {
        @Override
        public double cost(RoadGraph g, int e) {
            if (!RoadRules.walkAllowed(g.edgeRoadClass(e))) {
                return Double.POSITIVE_INFINITY;
            }
            return g.edgeLength(e) / kphToMps(toblerKph(g.edgeSlope(e)));
        }

        @Override
        public double maxSpeedMps(RoadGraph g) {
            return kphToMps(WALK_MAX_KPH);
        }
    },

    /**
     * 자전거: 평지 15km/h, 오르막은 경사 1%당 8% 감속(최소 3km/h), 내리막은 최대 30km/h
     */
    BIKE {
        @Override
        public double cost(RoadGraph g, int e) {
            if (!RoadRules.bikeAllowed(g.edgeRoadClass(e))) {
                return Double.POSITIVE_INFINITY;
            }
            return g.edgeLength(e) / kphToMps(bikeKph(g.edgeSlope(e)));
        }

        @Override
        public double maxSpeedMps(RoadGraph g) {
            return kphToMps(BIKE_MAX_KPH);
        }
    };

    static final double WALK_MAX_KPH = 6.0;
    static final double BIKE_BASE_KPH = 15.0;
    static final double BIKE_MIN_KPH = 3.0;
    static final double BIKE_MAX_KPH = 30.0;

    /**
     * 간선 e 를 지나는 데 걸리는 시간(초). 통행 불가면 +무한대
     */
    public abstract double cost(RoadGraph g, int e);

    /**
     * 휴리스틱용 최대 속도 (m/s)
     */
    public abstract double maxSpeedMps(RoadGraph g);

    static double toblerKph(double slope) {
        return WALK_MAX_KPH * Math.exp(-3.5 * Math.abs(slope + 0.05));
    }

    static double bikeKph(double slope) {
        if (slope >= 0) {
            return Math.max(BIKE_MIN_KPH, BIKE_BASE_KPH * (1 - 8 * slope));
        }
        return Math.min(BIKE_MAX_KPH, BIKE_BASE_KPH * (1 + 4 * -slope));
    }

    static double kphToMps(double kph) {
        return kph / 3.6;
    }
}
