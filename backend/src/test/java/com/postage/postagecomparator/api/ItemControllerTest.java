package com.postage.postagecomparator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ItemController.class)
@Import(GlobalExceptionHandler.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ItemService itemService;

    @Test
    void list_returnsItemsFromService() throws Exception {
        var item = new Item("id-1", "Box", "Desc", 100);
        given(itemService.findAll()).willReturn(List.of(item));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("id-1"))
                .andExpect(jsonPath("$[0].name").value("Box"));
    }

    @Test
    void get_whenItemExists_returns200() throws Exception {
        var item = new Item("id-1", "Box", "Desc", 100);
        given(itemService.findById("id-1")).willReturn(Optional.of(item));

        mockMvc.perform(get("/api/items/{id}", "id-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("Box"));
    }

    @Test
    void get_whenItemMissing_returns404() throws Exception {
        given(itemService.findById("missing-id")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/items/{id}", "missing-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_whenValid_returns201AndBody() throws Exception {
        var request = new Item(null, "Box", "Desc", 100);
        var created = new Item("id-1", "Box", "Desc", 100);
        given(itemService.create(any(Item.class))).willReturn(created);

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("Box"));
    }

    @Test
    void create_whenServiceThrowsBadRequest_returns400WithErrorBody() throws Exception {
        var request = new Item(null, "Box", "Desc", 0);

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("must be greater than 0"));
    }

    @Test
    void update_whenValid_returns200AndBody() throws Exception {
        var request = new Item(null, "New Box", "New desc", 200);
        var updated = new Item("id-1", "New Box", "New desc", 200);
        given(itemService.update(eq("id-1"), any(Item.class))).willReturn(updated);

        mockMvc.perform(put("/api/items/{id}", "id-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("New Box"));
    }

    @Test
    void update_whenSpuriousId_returns404() throws Exception {
        var request = new Item(null, "New Box", "New desc", 200);
        given(itemService.update(eq("non-existent-id"), any(Item.class)))
                .willThrow(new NotFoundException("Item with id non-existent-id not found"));

        mockMvc.perform(put("/api/items/{id}", "non-existent-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Item with id non-existent-id not found"));
    }

    @Test
    void update_whenBlankId_returns400() throws Exception {
        var request = new Item(null, "New Box", "New desc", 200);
        given(itemService.update(eq(" "), any(Item.class)))
                .willThrow(new BadRequestException("id must not be null or blank"));

        mockMvc.perform(put("/api/items/{id}", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("id must not be null or blank"));
    }

    @Test
    void delete_whenValid_returns204() throws Exception {
        doNothing().when(itemService).delete("id-1");

        mockMvc.perform(delete("/api/items/{id}", "id-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenSpuriousId_returns204() throws Exception {
        // Service doesn't throw for non-existent IDs, it just does nothing
        doNothing().when(itemService).delete("non-existent-id");

        mockMvc.perform(delete("/api/items/{id}", "non-existent-id"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenBlankId_returns400() throws Exception {
        // Service throws IllegalArgumentException for blank ID, which is handled by GlobalExceptionHandler
        // This will result in a 400 Bad Request
        org.mockito.BDDMockito.willThrow(new IllegalArgumentException("id must not be null or blank"))
                .given(itemService)
                .delete(" ");

        mockMvc.perform(delete("/api/items/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("id must not be null or blank"));
    }
}

