package com.closedtraverse.api;

import com.closedtraverse.api.TraverseModels.CreateRequest;
import com.closedtraverse.api.TraverseModels.CreatedResponse;
import com.closedtraverse.api.TraverseModels.TraverseResponse;
import com.closedtraverse.api.TraverseModels.TraverseSummary;
import com.closedtraverse.service.TraverseService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/traverses")
public class TraverseController {
    private final TraverseService service;

    public TraverseController(TraverseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreatedResponse> create(@RequestBody CreateRequest request) {
        UUID id = service.create(request);
        return ResponseEntity.created(URI.create("/traverses/" + id)).body(new CreatedResponse(id));
    }

    @GetMapping("/{id}")
    public TraverseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public List<TraverseSummary> list() {
        return service.list();
    }
}
