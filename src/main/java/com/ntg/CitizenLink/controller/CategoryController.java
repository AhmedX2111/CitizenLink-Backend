package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.request.CreateCategoryRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateCategoryRequest;
import com.ntg.CitizenLink.dto.agent.response.CategoryResponse;
import com.ntg.CitizenLink.service.interfaces.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'HANDLER', 'AGENT')")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        log.info("GET /api/v1/categories - fetching all active categories");

        List<CategoryResponse> categories = categoryService.getAllActiveCategories();

        log.info("GET /api/v1/categories - found {} categories", categories.size());
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        log.info("POST /api/v1/categories - creating category: nameEn={}", request.getNameEn());

        CategoryResponse response = categoryService.createCategory(request);

        log.info("POST /api/v1/categories - created category: id={}, code={}", response.getId(), response.getCode());
        return ResponseEntity.created(URI.create("/api/v1/categories/" + response.getId())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        log.info("PUT /api/v1/categories/{} - updating category", id);

        CategoryResponse response = categoryService.updateCategory(id, request);

        log.info("PUT /api/v1/categories/{} - updated successfully", id);
        return ResponseEntity.ok(response);
    }
}