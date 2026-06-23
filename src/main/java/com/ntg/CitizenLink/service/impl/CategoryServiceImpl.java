package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.response.CategoryResponse;
import com.ntg.CitizenLink.entities.Category;
import com.ntg.CitizenLink.repositories.CategoryRepository;
import com.ntg.CitizenLink.service.interfaces.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActiveCategories() {
        log.debug("Fetching all active categories sorted by sort order");

        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();

        return categories.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        log.debug("Fetching category by ID: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));

        return toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return categoryRepository.existsById(id);
    }

    /**
     * Convert Category entity to CategoryResponse DTO
     */
    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .nameEn(category.getNameEn())
                .nameAr(category.getNameAr())
                .active(category.getActive())
                .sortOrder(category.getSortOrder())
                .build();
    }
}
