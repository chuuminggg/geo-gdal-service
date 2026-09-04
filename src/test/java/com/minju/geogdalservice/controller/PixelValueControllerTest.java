package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.support.GdalIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PixelValueControllerTest extends GdalIntegrationTestSupport {

    @Test
    void getPixelValue_returnsValueAtLatLon() throws Exception {
        // (col 3, row 4) 픽셀 중심
        requestPixel(pixelToLonLat(3.5, 4.5), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pixelX").value(3))
                .andExpect(jsonPath("$.data.pixelY").value(4))
                .andExpect(jsonPath("$.data.bands.length()").value(1))
                .andExpect(jsonPath("$.data.bands[0].band").value(1))
                .andExpect(jsonPath("$.data.bands[0].dataType").value("Float32"))
                .andExpect(jsonPath("$.data.bands[0].value").value(43.0))
                .andExpect(jsonPath("$.data.bands[0].noData").value(false));
    }

    @Test
    void getPixelValue_noDataPixel() throws Exception {
        requestPixel(pixelToLonLat(0.5, 0.5), 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bands[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.bands[0].noData").value(true));
    }

    @Test
    void getPixelValue_outsideExtent_returnsBadRequest() throws Exception {
        requestPixel(pixelToLonLat(-5, 3), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getPixelValue_invalidBand_returnsBadRequest() throws Exception {
        requestPixel(pixelToLonLat(3.5, 4.5), 2)
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPixelValue_invalidLatitude_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/rasters/pixel")
                        .param("fileName", DEM_FILE)
                        .param("lat", "91")
                        .param("lon", "127"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions requestPixel(double[] lonLat, Integer band) throws Exception {
        var request = get("/api/rasters/pixel")
                .param("fileName", DEM_FILE)
                .param("lat", String.valueOf(lonLat[1]))
                .param("lon", String.valueOf(lonLat[0]));
        if (band != null) {
            request.param("band", String.valueOf(band));
        }
        return mockMvc.perform(request);
    }
}
