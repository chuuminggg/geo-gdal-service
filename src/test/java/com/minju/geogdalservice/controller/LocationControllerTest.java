package com.minju.geogdalservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minju.geogdalservice.dto.LocationDto;
import com.minju.geogdalservice.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "geo.storage.dir=build/test-storage")
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocationRepository locationRepository;

    @BeforeEach
    void setUp() throws Exception {
        locationRepository.deleteAll();
        List<LocationDto.Request> locations = List.of(
                location("서울시청", "landmark", 37.5663, 126.9779),
                location("광화문", "landmark", 37.5759, 126.9769),     // 시청에서 약 1.07km
                location("서울역", "station", 37.5547, 126.9707),      // 약 1.45km
                location("강남역", "station", 37.4979, 127.0276),      // 약 8.8km
                location("부산역", "station", 35.1151, 129.0422)       // 약 325km
        );
        mockMvc.perform(post("/api/locations/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locations)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }

    @Test
    void searchNearby_returnsLocationsWithinRadiusSortedByDistance() throws Exception {
        mockMvc.perform(get("/api/locations/nearby")
                        .param("lat", "37.5663")
                        .param("lon", "126.9779")
                        .param("radius", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", contains("서울시청", "광화문", "서울역")))
                .andExpect(jsonPath("$.data[0].distanceMeters", closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.data[1].distanceMeters", closeTo(1070, 30)));
    }

    @Test
    void searchNearby_filtersByCategoryAndLimit() throws Exception {
        mockMvc.perform(get("/api/locations/nearby")
                        .param("lat", "37.5663")
                        .param("lon", "126.9779")
                        .param("radius", "500000")
                        .param("category", "station")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", contains("서울역", "강남역")));
    }

    @Test
    void searchNearby_invalidRadius_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/locations/nearby")
                        .param("lat", "37.5663")
                        .param("lon", "126.9779")
                        .param("radius", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void register_invalidLatitude_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(location("잘못된 위치", null, 91.0, 127.0))))
                .andExpect(status().isBadRequest());
    }

    private static LocationDto.Request location(String name, String category, double lat, double lon) {
        return LocationDto.Request.builder()
                .name(name)
                .category(category)
                .latitude(lat)
                .longitude(lon)
                .build();
    }
}
