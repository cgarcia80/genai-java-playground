package com.genailab.newscurator.infrastructure.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genailab.newscurator.application.usecase.CurateNewsUseCase;
import com.genailab.newscurator.application.usecase.QueryNewsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsCuratorController.class)
class NewsCuratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CurateNewsUseCase curateUseCase;

    @MockitoBean
    private QueryNewsUseCase queryUseCase;

    @Test
    void whenCurateValid_thenReturnSuccess() throws Exception {
        // Arrange
        CurateRequest request = new CurateRequest("Java 21", 5);
        when(curateUseCase.curate("Java 21", 5)).thenReturn(5);

        // Act & Assert
        mockMvc.perform(post("/api/v1/curate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.itemsCurated").value(5));

        verify(curateUseCase, times(1)).curate("Java 21", 5);
    }

    @Test
    void whenCurateInvalid_thenReturnBadRequest() throws Exception {
        // Arrange
        CurateRequest request = new CurateRequest("", -1);

        // Act & Assert
        mockMvc.perform(post("/api/v1/curate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(curateUseCase);
    }

    @Test
    void whenQueryValid_thenReturnAnswer() throws Exception {
        // Arrange
        QueryRequest request = new QueryRequest("What is GraalVM?");
        QueryNewsUseCase.QueryResult queryResult = new QueryNewsUseCase.QueryResult(
                "GraalVM is a high-performance JDK.",
                List.of(new QueryNewsUseCase.SourceDto("GraalVM", "http://graalvm"))
        );
        when(queryUseCase.execute("What is GraalVM?")).thenReturn(queryResult);

        // Act & Assert
        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("GraalVM is a high-performance JDK."))
                .andExpect(jsonPath("$.sources[0].title").value("GraalVM"))
                .andExpect(jsonPath("$.sources[0].url").value("http://graalvm"));

        verify(queryUseCase, times(1)).execute("What is GraalVM?");
    }

    @Test
    void whenQueryInvalid_thenReturnBadRequest() throws Exception {
        // Arrange
        QueryRequest request = new QueryRequest("");

        // Act & Assert
        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryUseCase);
    }
}
