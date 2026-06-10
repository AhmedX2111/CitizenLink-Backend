package com.ntg.CitizenLink.controller;

import com.ntg.CitizenLink.entities.Category;
import com.ntg.CitizenLink.repositories.CategoryRepository;
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

    private final CategoryRepository categoryRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<Category>> getAllCategories() {
        log.info("Fetching all active categories");
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        return ResponseEntity.ok(categories);
    }
}