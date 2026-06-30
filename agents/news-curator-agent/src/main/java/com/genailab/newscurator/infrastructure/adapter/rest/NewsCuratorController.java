package com.genailab.newscurator.infrastructure.adapter.rest;

import com.genailab.newscurator.application.usecase.CurateNewsUseCase;
import com.genailab.newscurator.application.usecase.QueryNewsUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NewsCuratorController {
    private final CurateNewsUseCase curateUseCase;
    private final QueryNewsUseCase queryUseCase;

    public NewsCuratorController(CurateNewsUseCase curateUseCase, QueryNewsUseCase queryUseCase) {
        this.curateUseCase = curateUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping("/curate")
    public ResponseEntity<CurateResponse> curate(@Valid @RequestBody CurateRequest request) {
        int itemsCurated = curateUseCase.curate(request.topic(), request.limit());
        return ResponseEntity.ok(new CurateResponse("success", itemsCurated));
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        QueryNewsUseCase.QueryResult result = queryUseCase.execute(request.question());
        return ResponseEntity.ok(new QueryResponse(result.answer(), result.sources()));
    }
}

// Request/Response Records
record CurateRequest(
    @NotBlank(message = "Topic is required") String topic,
    @NotNull(message = "Limit is required") @Min(value = 1, message = "Limit must be positive") Integer limit
) {}

record CurateResponse(String status, int itemsCurated) {}

record QueryRequest(
    @NotBlank(message = "Question is required") String question
) {}

record QueryResponse(
    String answer,
    List<QueryNewsUseCase.SourceDto> sources
) {}
