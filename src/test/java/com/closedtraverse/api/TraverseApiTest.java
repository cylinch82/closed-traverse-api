package com.closedtraverse.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TraverseApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsRetrievesAndListsPersistedTraverse() throws Exception {
        String response = mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mvc.perform(get("/traverses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Taipei"))
                .andExpect(jsonPath("$.adjustmentVersion").value("LENGTH_PROPORTIONAL_V1"))
                .andExpect(jsonPath("$.totalLengthM").value(99.986))
                .andExpect(jsonPath("$.legs.length()").value(4))
                .andExpect(jsonPath("$.legs[0].distanceM").value(30.004))
                .andExpect(jsonPath("$.legs[0].azimuthDeg").value(89.98))
                .andExpect(jsonPath("$.legs[3].toStationNo").value("S1"));

        String detail = mvc.perform(get("/traverses/{id}", id)).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(detail);
        double correctionDx = 0;
        double correctionDy = 0;
        for (JsonNode leg : data.get("legs")) {
            correctionDx += leg.get("correctionDxM").asDouble();
            correctionDy += leg.get("correctionDyM").asDouble();
        }
        org.junit.jupiter.api.Assertions.assertEquals(-data.get("closureDxM").asDouble(), correctionDx, 1e-12);
        org.junit.jupiter.api.Assertions.assertEquals(-data.get("closureDyM").asDouble(), correctionDy, 1e-12);
        org.junit.jupiter.api.Assertions.assertEquals(0, data.get("legs").get(3).get("adjustedEndXM").asDouble(), 1e-12);
        org.junit.jupiter.api.Assertions.assertEquals(0, data.get("legs").get(3).get("adjustedEndYM").asDouble(), 1e-12);

        mvc.perform(get("/traverses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id.toString())));
    }

    @Test
    void rejectsBadInputsWithoutSaving() throws Exception {
        String before = mvc.perform(get("/traverses")).andReturn().getResponse().getContentAsString();
        int countBefore = objectMapper.readTree(before).size();
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson().replace("Asia/Taipei", "Invalid/Zone")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TRAVERSE"));
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson().replace("\"toStationNo\":\"S3\"", "\"toStationNo\":\"WRONG\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson().replace("\"distanceM\":30.004", "\"distanceM\":-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson().replace("\"azimuthDeg\":89.98", "\"azimuthDeg\":360")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson().replace("\"distanceM\":30.004", "\"distanceM\":1e309")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON)
                        .content(exampleJson().replace("Asia/Taipei", "A".repeat(101))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/traverses").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        String after = mvc.perform(get("/traverses")).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(countBefore, objectMapper.readTree(after).size());
    }

    @Test
    void missingTraverseReturns404() throws Exception {
        mvc.perform(get("/traverses/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVERSE_NOT_FOUND"));
    }

    private String exampleJson() {
        return """
                {"timeZone":"Asia/Taipei","legs":[
                  {"fromStationNo":"S1","toStationNo":"S2","distanceM":30.004,"azimuthDeg":89.98},
                  {"fromStationNo":"S2","toStationNo":"S3","distanceM":20.006,"azimuthDeg":0.03},
                  {"fromStationNo":"S3","toStationNo":"S4","distanceM":29.991,"azimuthDeg":269.97},
                  {"fromStationNo":"S4","toStationNo":"S1","distanceM":19.985,"azimuthDeg":180.02}
                ]}
                """;
    }
}
