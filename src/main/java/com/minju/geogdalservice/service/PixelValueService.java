package com.minju.geogdalservice.service;

import com.minju.geogdalservice.common.exception.GeoServiceException;
import com.minju.geogdalservice.dto.PixelValueDto;
import com.minju.geogdalservice.util.GdalRasterSupport;
import com.minju.geogdalservice.util.GdalRasterSupport.PixelPosition;
import lombok.RequiredArgsConstructor;
import org.gdal.gdal.Band;
import org.gdal.gdal.gdal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PixelValueService {

    private final GdalRasterSupport gdalRasterSupport;

    // 위경도(WGS84)에 해당하는 픽셀 값 추출. band가 null이면 전체 밴드
    public PixelValueDto getPixelValue(String fileName, double lat, double lon, Integer band) {
        return gdalRasterSupport.withDataset(fileName, (path, dataset) -> {
            int bandCount = dataset.GetRasterCount();
            if (band != null && (band < 1 || band > bandCount)) {
                throw GeoServiceException.badRequest("band must be between 1 and " + bandCount);
            }

            PixelPosition position = gdalRasterSupport.toPixelPosition(dataset, lon, lat);
            if (!position.isInside()) {
                throw GeoServiceException.badRequest(
                        "Coordinate (lat=" + lat + ", lon=" + lon + ") is outside the raster extent");
            }

            int from = band != null ? band : 1;
            int to = band != null ? band : bandCount;
            List<PixelValueDto.BandValue> values = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                Band rasterBand = dataset.GetRasterBand(i);
                double raw = gdalRasterSupport.readRawValue(rasterBand, position.col(), position.row());
                boolean noData = gdalRasterSupport.isNoData(rasterBand, raw);
                values.add(PixelValueDto.BandValue.builder()
                        .band(i)
                        .dataType(gdal.GetDataTypeName(rasterBand.getDataType()))
                        .value(noData ? null : raw)
                        .noData(noData)
                        .build());
            }

            return PixelValueDto.builder()
                    .fileName(fileName)
                    .lat(lat)
                    .lon(lon)
                    .pixelX(position.col())
                    .pixelY(position.row())
                    .bands(values)
                    .build();
        });
    }
}
