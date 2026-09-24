package com.minju.geogdalservice.support;

import com.minju.geogdalservice.gis.GeometryUtils;
import com.minju.geogdalservice.gis.VectorFeature;
import org.locationtech.jts.geom.Coordinate;

import java.util.HashMap;
import java.util.Map;

public final class TestFeatures {

    private TestFeatures() {
    }

    // line(attrs, lon1, lat1, lon2, lat2, ...)
    public static VectorFeature line(Map<String, String> attrs, double... lonLat) {
        Coordinate[] cs = new Coordinate[lonLat.length / 2];
        for (int i = 0; i < cs.length; i++) {
            cs[i] = new Coordinate(lonLat[i * 2], lonLat[i * 2 + 1]);
        }
        return new VectorFeature(GeometryUtils.FACTORY.createLineString(cs), new HashMap<>(attrs));
    }

    public static VectorFeature line(double... lonLat) {
        return line(Map.of(), lonLat);
    }

    public static VectorFeature point(String name, double lon, double lat) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("name", name);
        return new VectorFeature(GeometryUtils.point(lon, lat), attrs);
    }
}
