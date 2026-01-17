package com.postage.postagecomparator.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.service.PackagingService;
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

@WebMvcTest(controllers = PackagingController.class)
@Import(GlobalExceptionHandler.class)
class PackagingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PackagingService packagingService;

    @Test
    void list_returnsPackagingsFromService() throws Exception {
        var packaging = new Packaging("id-1", "Box", "Desc", 10, 10, 10, 1000, 1.0);
        given(packagingService.findAll()).willReturn(List.of(packaging));

        mockMvc.perform(get("/api/packaging"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("id-1"))
                .andExpect(jsonPath("$[0].name").value("Box"));
    }

    @Test
    void get_whenPackagingExists_returns200() throws Exception {
        var packaging = new Packaging("id-1", "Box", "Desc", 10, 10, 10, 1000, 1.0);
        given(packagingService.findById("id-1")).willReturn(Optional.of(packaging));

        mockMvc.perform(get("/api/packaging/{id}", "id-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("Box"));
    }

    @Test
    void get_whenPackagingMissing_returns404() throws Exception {
        given(packagingService.findById("missing-id")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/packaging/{id}", "missing-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_whenValid_returns201AndBody() throws Exception {
        var request = new Packaging(null, "Box", "Desc", 10, 10, 10, 1000, 1.0);
        var created = new Packaging("id-1", "Box", "Desc", 10, 10, 10, 1000, 1.0);
        given(packagingService.create(any(Packaging.class))).willReturn(created);

        mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("Box"));
    }

    @Test
    void create_whenLengthInvalid_returns400() throws Exception {
        var request = new Packaging(null, "Box", "Desc", 0, 10, 10, 1000, 1.0);

        mockMvc.perform(post("/api/packaging")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("must be greater than 0"));
    }

    @Test
    void update_whenSpuriousId_returns404() throws Exception {
        var request = new Packaging(null, "Box", "Desc", 10, 10, 10, 1000, 1.0);
        given(packagingService.update(eq("missing-id"), any(Packaging.class)))
                .willThrow(new NotFoundException("Packaging with id missing-id not found"));

        mockMvc.perform(put("/api/packaging/{id}", "missing-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Packaging with id missing-id not found"));
    }

    @Test
    void update_whenBlankId_returns400() throws Exception {
        var request = new Packaging(null, "Box", "Desc", 10, 10, 10, 1000, 1.0);
        given(packagingService.update(eq(" "), any(Packaging.class)))
                .willThrow(new BadRequestException("id must not be null or blank"));

        mockMvc.perform(put("/api/packaging/{id}", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("id must not be null or blank"));
    }

    @Test
    void delete_whenValid_returns204() throws Exception {
        doNothing().when(packagingService).delete("id-1");

        mockMvc.perform(delete("/api/packaging/{id}", "id-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_whenBlankId_returns400() throws Exception {
        org.mockito.BDDMockito.willThrow(new IllegalArgumentException("id must not be null or blank"))
                .given(packagingService)
                .delete(" ");

        mockMvc.perform(delete("/api/packaging/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("id must not be null or blank"));
    }
}
