package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.support.GdalIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoordinateTransformControllerTest extends GdalIntegrationTestSupport {

    @Test
    void transformOne_wgs84ToUtm52n() throws Exception {
        // UTM 52N 중앙 자오선(129°E)과 적도의 교점 = (500000, 0)
        mockMvc.perform(get("/api/coordinates/transform")
                        .param("sourceCrs", "EPSG:4326")
                        .param("targetCrs", "EPSG:32652")
                        .param("x", "129")
                        .param("y", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].x", closeTo(500_000, 0.001)))
                .andExpect(jsonPath("$.data.points[0].y", closeTo(0, 0.001)));
    }

    @Test
    void transform_wgs84ToKoreaCentralBelt() throws Exception {
        // EPSG:5186 원점(38°N, 127°E) = (200000, 600000)
        String body = """
                {
                  "sourceCrs": "EPSG:4326",
                  "targetCrs": "EPSG:5186",
                  "points": [
                    {"x": 127.0, "y": 38.0},
                    {"x": 126.9779, "y": 37.5663, "z": 10.0}
                  ]
                }
                """;
        mockMvc.perform(post("/api/coordinates/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points.length()").value(2))
                .andExpect(jsonPath("$.data.points[0].x", closeTo(200_000, 0.001)))
                .andExpect(jsonPath("$.data.points[0].y", closeTo(600_000, 0.001)))
                .andExpect(jsonPath("$.data.points[0].z").doesNotExist())
                // 서울시청: 원점 기준 서쪽 약 1.95km, 남쪽 약 48.1km (구면 근사값)
                .andExpect(jsonPath("$.data.points[1].x", closeTo(198_050, 50)))
                .andExpect(jsonPath("$.data.points[1].y", closeTo(551_880, 50)))
                .andExpect(jsonPath("$.data.points[1].z", closeTo(10.0, 0.001)));
    }

    @Test
    void transform_invalidCrs_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/coordinates/transform")
                        .param("sourceCrs", "EPSG:4326")
                        .param("targetCrs", "EPSG:999999")
                        .param("x", "127")
                        .param("y", "37"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void transform_emptyPoints_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/coordinates/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCrs\":\"EPSG:4326\",\"targetCrs\":\"EPSG:5186\",\"points\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
