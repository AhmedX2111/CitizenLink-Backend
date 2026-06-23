package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.dto.agent.response.CategoryResponse;
import com.ntg.CitizenLink.entities.Category;
import com.ntg.CitizenLink.repositories.CategoryRepository;
import com.ntg.CitizenLink.service.interfaces.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}