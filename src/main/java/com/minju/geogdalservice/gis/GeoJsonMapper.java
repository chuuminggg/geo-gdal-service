package com.minju.geogdalservice.gis;

import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JTS Geometry -> GeoJSON geometry 객체(Map) 변환.
 * 응답 DTO에 그대로 넣으면 Jackson이 GeoJSON으로 직렬화한다.
 */
public final class GeoJsonMapper {

    private GeoJsonMapper() {
    }

    public static Map<String, Object> toGeoJson(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", geometry.getGeometryType());
        if (geometry instanceof GeometryCollection && !(geometry instanceof MultiPoint)
                && !(geometry instanceof MultiLineString) && !(geometry instanceof MultiPolygon)) {
            List<Object> geometries = new ArrayList<>();
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                geometries.add(toGeoJson(geometry.getGeometryN(i)));
            }
            json.put("geometries", geometries);
            return json;
        }
        json.put("coordinates", coordinates(geometry));
        return json;
    }

    private static Object coordinates(Geometry g) {
        if (g instanceof Point p) {
            return position(p.getCoordinate());
        }
        if (g instanceof LineString l) {
            return positions(l.getCoordinates());
        }
        if (g instanceof Polygon p) {
            List<Object> rings = new ArrayList<>();
            rings.add(positions(p.getExteriorRing().getCoordinates()));
            for (int i = 0; i < p.getNumInteriorRing(); i++) {
                rings.add(positions(p.getInteriorRingN(i).getCoordinates()));
            }
            return rings;
        }
        List<Object> parts = new ArrayList<>();
        for (int i = 0; i < g.getNumGeometries(); i++) {
            parts.add(coordinates(g.getGeometryN(i)));
        }
        return parts;
    }

    public static List<double[]> positions(Coordinate[] coordinates) {
        List<double[]> list = new ArrayList<>(coordinates.length);
        for (Coordinate c : coordinates) {
            list.add(position(c));
        }
        return list;
    }

    private static double[] position(Coordinate c) {
        return new double[]{c.x, c.y};
    }
}
