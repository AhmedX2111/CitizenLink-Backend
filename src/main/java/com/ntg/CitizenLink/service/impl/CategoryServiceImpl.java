package com.ntg.CitizenLink.service.impl;

import com.ntg.CitizenLink.GEH.ResourceNotFoundException;
import com.ntg.CitizenLink.dto.agent.request.CreateCategoryRequest;
import com.ntg.CitizenLink.dto.agent.request.UpdateCategoryRequest;
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

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String code = generateCode(request.getNameEn());
        log.info("Creating category: nameEn={}, code={}", request.getNameEn(), code);

        Category category = new Category();
        category.setCode(code);
        category.setNameEn(request.getNameEn());
        category.setNameAr(request.getNameAr());
        category.setActive(request.getActive() != null ? request.getActive() : true);

        Category saved = categoryRepository.save(category);
        log.info("Category created: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        log.info("Updating category: id={}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));

        category.setNameEn(request.getNameEn());
        category.setNameAr(request.getNameAr());
        category.setActive(request.getActive());

        Category saved = categoryRepository.save(category);
        log.info("Category updated: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    private String generateCode(String nameEn) {
        String base = nameEn.toUpperCase()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Z0-9_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (base.isEmpty()) base = "CATEGORY";
        if (base.length() > 50) base = base.substring(0, 50);

        String candidate = base;
        int suffix = 2;
        while (categoryRepository.findByCode(candidate).isPresent()) {
            String suffixStr = "_" + suffix;
            int maxLen = 50 - suffixStr.length();
            candidate = (base.length() > maxLen ? base.substring(0, maxLen) : base) + suffixStr;
            suffix++;
        }
        return candidate;
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
