package com.bakeaura.category;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductService productService;

    public List<CategoryResponseDto> getAllCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = "categories", key = "#id")
    public CategoryResponseDto getCategoryById(Long id) {
        return toResponse(getCategoryOrThrow(id));
    }

    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponseDto createCategory(CategoryRequestDto request) {
        String name = normalizeName(request.getName());

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Category already exists");
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());

        return toResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponseDto updateCategory(Long id, CategoryRequestDto request) {
        Category category = getCategoryOrThrow(id);
        String name = normalizeName(request.getName());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BadRequestException("Category already exists");
        }

        category.setName(name);
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());

        return toResponse(categoryRepository.save(category));
    }

    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(Long id) {
        Category category = getCategoryOrThrow(id);

        if (productService.existsByCategory(id)) {
            throw new BadRequestException("Cannot delete category because products are assigned to it");
        }

        categoryRepository.delete(category);
    }

    /**
     * Evicts all entries from the categories cache without modifying data.
     * Called by the admin cache-evict endpoint when a direct SQL insert or
     * external seed script has written to the categories table, bypassing
     * the normal service layer and leaving Redis holding stale data.
     */
    @CacheEvict(value = "categories", allEntries = true)
    public void evictCategoriesCache() {
        // Body intentionally empty. The @CacheEvict interceptor fires on method
        // entry via Spring AOP proxy and clears all "categories" cache entries.
    }

    public long countCategories() {
        return categoryRepository.count();
    }

    private Category getCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private CategoryResponseDto toResponse(Category category) {
        return new CategoryResponseDto(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl()
        );
    }
}
