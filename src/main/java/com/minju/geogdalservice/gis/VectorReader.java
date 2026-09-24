package com.minju.geogdalservice.gis;

import com.minju.geogdalservice.util.GdalInitializer;
import lombok.RequiredArgsConstructor;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import org.gdal.osr.osrConstants;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GDAL/OGR 로 벡터 파일(GeoJSON, Shapefile(zip), GeoPackage 등)을 읽어
 * EPSG:4326 JTS Geometry 로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class VectorReader {

    private final GdalInitializer gdalInitializer;

    public VectorData read(Path file) {
        gdalInitializer.requireAvailable();

        // 압축된 Shapefile 은 GDAL 가상 파일시스템으로 압축 해제 없이 읽는다
        String path = file.toString().toLowerCase(Locale.ROOT).endsWith(".zip")
                ? "/vsizip/" + file
                : file.toString();

        DataSource ds = ogr.Open(path, 0);
        if (ds == null) {
            throw new IllegalArgumentException("GDAL/OGR로 열 수 없는 벡터 파일입니다: " + gdal.GetLastErrorMsg());
        }
        try {
            if (ds.GetLayerCount() == 0) {
                throw new IllegalArgumentException("레이어가 없는 벡터 파일입니다.");
            }
            return readLayer(ds.GetLayer(0));
        } finally {
            ds.delete();
        }
    }

    private VectorData readLayer(Layer layer) {
        SpatialReference source = layer.GetSpatialRef();
        // 좌표계 정보가 없으면 GeoJSON 표준(RFC 7946)에 따라 WGS84 로 간주
        Integer sourceEpsg = GeometryUtils.WGS84;
        CoordinateTransformation ct = null;
        SpatialReference wgs84 = GdalSrs.wgs84();

        if (source != null) {
            source = source.Clone();
            source.SetAxisMappingStrategy(osrConstants.OAMS_TRADITIONAL_GIS_ORDER);
            sourceEpsg = GdalSrs.identifyEpsg(source);
            if (source.IsSame(wgs84) != 1) {
                ct = CoordinateTransformation.CreateCoordinateTransformation(source, wgs84);
            }
        }

        try {
            WKBReader wkbReader = new WKBReader(GeometryUtils.FACTORY);
            List<VectorFeature> features = new ArrayList<>();
            Feature feature;
            while ((feature = layer.GetNextFeature()) != null) {
                try {
                    features.add(toVectorFeature(feature, ct, wkbReader));
                } finally {
                    feature.delete();
                }
            }
            return new VectorData(features, sourceEpsg);
        } finally {
            if (ct != null) ct.delete();
            wgs84.delete();
        }
    }

    private VectorFeature toVectorFeature(Feature feature, CoordinateTransformation ct, WKBReader wkbReader) {
        Geometry geometry = null;
        org.gdal.ogr.Geometry ogrGeometry = feature.GetGeometryRef();
        if (ogrGeometry != null) {
            if (ct != null) {
                ogrGeometry.Transform(ct);
            }
            try {
                geometry = wkbReader.read(ogrGeometry.ExportToWkb());
                geometry.setSRID(GeometryUtils.WGS84);
            } catch (ParseException e) {
                geometry = null;   // 해석 불가 geometry 는 검수 단계에서 무효로 집계
            }
        }

        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < feature.GetFieldCount(); i++) {
            String name = feature.GetFieldDefnRef(i).GetName().toLowerCase(Locale.ROOT);
            attributes.put(name, feature.IsFieldSetAndNotNull(i) ? feature.GetFieldAsString(i) : null);
        }
        return new VectorFeature(geometry, attributes);
    }
}
