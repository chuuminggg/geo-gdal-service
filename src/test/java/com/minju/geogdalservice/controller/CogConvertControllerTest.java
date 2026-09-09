package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.support.GdalIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CogConvertControllerTest extends GdalIntegrationTestSupport {

    @Test
    void convert_createsValidCogAndKeepsPixelValues() throws Exception {
        String body = """
                {"fileName": "%s", "outputFileName": "dem_cog_deflate.tif", "compression": "deflate", "blockSize": 256}
                """.formatted(DEM_FILE);

        mockMvc.perform(post("/api/rasters/cog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceFileName").value(DEM_FILE))
                .andExpect(jsonPath("$.data.outputFileName").value("dem_cog_deflate.tif"))
                .andExpect(jsonPath("$.data.compression").value("DEFLATE"))
                .andExpect(jsonPath("$.data.blockSize").value(256))
                .andExpect(jsonPath("$.data.layout").value("COG"));

        // 변환된 COG에서도 같은 위치의 값이 유지된다
        double[] lonLat = pixelToLonLat(3.5, 4.5);
        mockMvc.perform(get("/api/rasters/pixel")
                        .param("fileName", "dem_cog_deflate.tif")
                        .param("lat", String.valueOf(lonLat[1]))
                        .param("lon", String.valueOf(lonLat[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bands[0].value").value(43.0));

        mockMvc.perform(get("/api/rasters/files/download").param("fileName", "dem_cog_deflate.tif"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/tiff"));
    }

    @Test
    void convert_defaultOutputName_andConflictWithoutOverwrite() throws Exception {
        String body = "{\"fileName\": \"" + DEM_FILE + "\", \"overwrite\": true}";
        mockMvc.perform(post("/api/rasters/cog").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outputFileName").value("test_dem_cog.tif"))
                .andExpect(jsonPath("$.data.compression").value("DEFLATE"))
                .andExpect(jsonPath("$.data.layout").value("COG"));

        String noOverwrite = "{\"fileName\": \"" + DEM_FILE + "\"}";
        mockMvc.perform(post("/api/rasters/cog").contentType(MediaType.APPLICATION_JSON).content(noOverwrite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void convert_invalidCompression_returnsBadRequest() throws Exception {
        String body = "{\"fileName\": \"" + DEM_FILE + "\", \"compression\": \"RAR\"}";
        mockMvc.perform(post("/api/rasters/cog").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
