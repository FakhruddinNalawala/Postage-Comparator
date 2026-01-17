package com.postage.postagecomparator.integration;

import com.postage.postagecomparator.model.Item;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemsIntegrationTest extends IntegrationTestBase {

    @Test
    void postThenGet_returnsPersistedItem() throws Exception {
        var request = new Item(null, "Box", "Small box", 100);

        var created = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Box"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdItem = objectMapper.readValue(created, Item.class);

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Box"));

        mockMvc.perform(get("/api/items/{id}", createdItem.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdItem.id()))
                .andExpect(jsonPath("$.name").value("Box"));
    }

    @Test
    void postThenGetThenPutThenGet_updatesPersistedItem() throws Exception {
        var request = new Item(null, "Box", "Small box", 100);

        var created = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdItem = objectMapper.readValue(created, Item.class);

        var updateRequest = new Item(null, "Updated Box", "Updated desc", 250);

        mockMvc.perform(put("/api/items/{id}", createdItem.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(createdItem.id()))
                .andExpect(jsonPath("$.name").value("Updated Box"))
                .andExpect(jsonPath("$.unitWeightGrams").value(250));

        mockMvc.perform(get("/api/items/{id}", createdItem.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Updated Box"))
                .andExpect(jsonPath("$.unitWeightGrams").value(250));
    }

    @Test
    void postDuplicateName_returns400() throws Exception {
        var request = new Item(null, "Box", "Small box", 100);

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void putUnknownId_returns404() throws Exception {
        var request = new Item(null, "New Box", "Desc", 100);

        mockMvc.perform(put("/api/items/{id}", "missing-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void deleteThenList_returnsEmpty() throws Exception {
        var request = new Item(null, "Box", "Small box", 100);

        var created = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdItem = objectMapper.readValue(created, Item.class);

        mockMvc.perform(delete("/api/items/{id}", createdItem.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/items/{id}", createdItem.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postThenGetThenDeleteThenGet_returns404() throws Exception {
        var request = new Item(null, "Box", "Small box", 100);

        var created = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var createdItem = objectMapper.readValue(created, Item.class);

        mockMvc.perform(get("/api/items/{id}", createdItem.id()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/items/{id}", createdItem.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/items/{id}", createdItem.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postTwoItems_thenGetList_returnsBoth() throws Exception {
        var first = new Item(null, "Box", "Small box", 100);
        var second = new Item(null, "Envelope", "Flat", 50);

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
