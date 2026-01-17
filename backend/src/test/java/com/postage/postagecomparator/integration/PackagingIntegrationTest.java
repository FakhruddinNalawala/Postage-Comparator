package com.postage.postagecomparator.integration;

import com.postage.postagecomparator.model.Packaging;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PackagingIntegrationTest extends IntegrationTestBase {

    @Test
    void postThenGet_returnsPersistedPackaging() throws Exception {
        var request = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        var created = mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Box"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdPackaging = objectMapper.readValue(created, Packaging.class);

        mockMvc.perform(get("/api/packaging"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Box"));

        mockMvc.perform(get("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdPackaging.id()))
                .andExpect(jsonPath("$.name").value("Box"));
    }

    @Test
    void postThenGetThenPutThenGet_updatesPersistedPackaging() throws Exception {
        var request = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        var created = mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdPackaging = objectMapper.readValue(created, Packaging.class);

        var updateRequest = new Packaging(null, "Updated Box", "Updated desc", 12, 12, 12, 0, 1.5);

        mockMvc.perform(put("/api/packaging/{id}", createdPackaging.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdPackaging.id()))
                .andExpect(jsonPath("$.name").value("Updated Box"))
                .andExpect(jsonPath("$.lengthCm").value(12))
                .andExpect(jsonPath("$.packagingCostAud").value(1.5));

        mockMvc.perform(get("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Updated Box"))
                .andExpect(jsonPath("$.lengthCm").value(12));
    }

    @Test
    void postDuplicateName_returns400() throws Exception {
        var request = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void putUnknownId_returns404() throws Exception {
        var request = new Packaging(null, "New Box", "Desc", 10, 10, 10, 1000, 1.0);

        mockMvc.perform(put("/api/packaging/{id}", "missing-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void deleteThenList_returnsEmpty() throws Exception {
        var request = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        var created = mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdPackaging = objectMapper.readValue(created, Packaging.class);

        mockMvc.perform(delete("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/packaging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postThenGetThenDeleteThenGet_returns404() throws Exception {
        var request = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);

        var created = mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdPackaging = objectMapper.readValue(created, Packaging.class);

        mockMvc.perform(get("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/packaging/{id}", createdPackaging.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postTwoPackagings_thenGetList_returnsBoth() throws Exception {
        var first = new Packaging(null, "Box", "Small box", 10, 10, 10, 1000, 1.0);
        var second = new Packaging(null, "Satchel", "Flat", 30, 25, 2, 1500, 0.5);

        mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/packaging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
