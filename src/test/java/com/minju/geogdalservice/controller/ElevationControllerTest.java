package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.support.GdalIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ElevationControllerTest extends GdalIntegrationTestSupport {

    @Test
    void getElevation_nearest() throws Exception {
        double[] lonLat = pixelToLonLat(3.9, 4.1);
        mockMvc.perform(get("/api/elevation")
                        .param("fileName", DEM_FILE)
                        .param("lat", String.valueOf(lonLat[1]))
                        .param("lon", String.valueOf(lonLat[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unit").value("m"))
                .andExpect(jsonPath("$.data.band").value(1))
                .andExpect(jsonPath("$.data.interpolation").value("NEAREST"))
                .andExpect(jsonPath("$.data.results[0].status").value("OK"))
                .andExpect(jsonPath("$.data.results[0].elevation", closeTo(43.0, 1e-6)));
    }

    @Test
    void getElevation_bilinear() throws Exception {
        // (col 3, row 4) 중심과 (col 4, row 4) 중심의 정중앙 → 43.5
        double[] lonLat = pixelToLonLat(4.0, 4.5);
        mockMvc.perform(get("/api/elevation")
                        .param("fileName", DEM_FILE)
                        .param("lat", String.valueOf(lonLat[1]))
                        .param("lon", String.valueOf(lonLat[0]))
                        .param("interpolation", "BILINEAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interpolation").value("BILINEAR"))
                .andExpect(jsonPath("$.data.results[0].elevation", closeTo(43.5, 1e-3)));
    }

    @Test
    void getElevations_batchWithNoDataAndOutOfBounds() throws Exception {
        double[] center = pixelToLonLat(5.5, 6.5);       // 65
        double[] between = pixelToLonLat(5.5, 7.0);      // 65, 75 사이 → 70
        double[] noData = pixelToLonLat(0.5, 0.5);       // NoData
        double[] outside = pixelToLonLat(20, 20);
        String body = """
                {
                  "fileName": "%s",
                  "interpolation": "BILINEAR",
                  "points": [
                    {"lat": %s, "lon": %s},
                    {"lat": %s, "lon": %s},
                    {"lat": %s, "lon": %s},
                    {"lat": %s, "lon": %s}
                  ]
                }
                """.formatted(DEM_FILE,
                center[1], center[0], between[1], between[0], noData[1], noData[0], outside[1], outside[0]);

        mockMvc.perform(post("/api/elevation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(4))
                .andExpect(jsonPath("$.data.results[0].elevation", closeTo(65.0, 1e-3)))
                .andExpect(jsonPath("$.data.results[1].elevation", closeTo(70.0, 1e-3)))
                .andExpect(jsonPath("$.data.results[2].status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.results[2].elevation").doesNotExist())
                .andExpect(jsonPath("$.data.results[3].status").value("OUT_OF_BOUNDS"));
    }

    @Test
    void getElevation_invalidInterpolation_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/elevation")
                        .param("fileName", DEM_FILE)
                        .param("lat", "37.9")
                        .param("lon", "126.7")
                        .param("interpolation", "CUBIC"))
                .andExpect(status().isBadRequest());
    }
}
