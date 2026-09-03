package com.minju.geogdalservice.controller;

import com.minju.geogdalservice.support.GdalIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetadataControllerTest extends GdalIntegrationTestSupport {

    @Test
    void extract_readsGeoTiffMetadataAndSaves() throws Exception {
        mockMvc.perform(post("/api/metadata/extract").param("fileName", DEM_FILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value(DEM_FILE))
                .andExpect(jsonPath("$.data.width").value(DEM_SIZE))
                .andExpect(jsonPath("$.data.height").value(DEM_SIZE))
                .andExpect(jsonPath("$.data.bandCount").value(1))
                .andExpect(jsonPath("$.data.driver").value("GTiff"))
                .andExpect(jsonPath("$.data.dataType").value("Float32"))
                .andExpect(jsonPath("$.data.crs").value("EPSG:32652"))
                .andExpect(jsonPath("$.data.pixelSizeX", closeTo(PIXEL_SIZE, 1e-9)))
                .andExpect(jsonPath("$.data.minX", closeTo(ORIGIN_X, 1e-6)))
                .andExpect(jsonPath("$.data.maxX", closeTo(ORIGIN_X + DEM_SIZE * PIXEL_SIZE, 1e-6)))
                .andExpect(jsonPath("$.data.minY", closeTo(ORIGIN_Y - DEM_SIZE * PIXEL_SIZE, 1e-6)))
                .andExpect(jsonPath("$.data.maxY", closeTo(ORIGIN_Y, 1e-6)));

        // 같은 파일을 다시 추출해도 중복 저장되지 않는다
        mockMvc.perform(post("/api/metadata/extract").param("fileName", DEM_FILE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/metadata").param("fileName", DEM_FILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].crs").value("EPSG:32652"));
    }

    @Test
    void extract_missingFile_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/metadata/extract").param("fileName", "missing.tif"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void extract_pathTraversal_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/metadata/extract").param("fileName", "../secret.tif"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_storesTiffAndRejectsOtherExtensions() throws Exception {
        mockMvc.perform(multipart("/api/rasters/files")
                        .file(new MockMultipartFile("file", "uploaded.tif", "image/tiff", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("uploaded.tif"))
                .andExpect(jsonPath("$.data.size").value(3));

        mockMvc.perform(get("/api/rasters/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].fileName", hasItem("uploaded.tif")));

        mockMvc.perform(multipart("/api/rasters/files")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isBadRequest());
    }
}
