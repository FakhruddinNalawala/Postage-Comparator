package com.postage.postagecomparator.api;

import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.service.PackagingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packaging")
public class PackagingController {

    private final PackagingService packagingService;

    public PackagingController(PackagingService packagingService) {
        this.packagingService = packagingService;
    }

    @GetMapping
    public List<Packaging> list() {
        return packagingService.findAll();
    }

    @PostMapping
    public ResponseEntity<Packaging> create(@RequestBody @Valid Packaging packaging) {
        Packaging created = packagingService.create(packaging);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Packaging> get(@PathVariable String id) {
        return packagingService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Packaging> update(@PathVariable String id, @RequestBody @Valid Packaging packaging) {
        Packaging updated = packagingService.update(id, packaging);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        packagingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
