package com.ntg.CitizenLink.service.interfaces;

import com.ntg.CitizenLink.dto.agent.request.CreateCategoryRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateCategoryRequest;
import com.ntg.CitizenLink.dto.agent.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    /**
     * Get all active categories sorted by sort order
     */
    List<CategoryResponse> getAllActiveCategories();

    /**
     * Get category by ID
     */
    CategoryResponse getCategoryById(UUID id);

    /**
     * Check if category exists
     */
    boolean existsById(UUID id);

    /**
     * Create a new category
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Update an existing category
     */
    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request);
}

