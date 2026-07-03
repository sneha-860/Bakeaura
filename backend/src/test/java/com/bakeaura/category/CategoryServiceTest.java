package com.bakeaura.category;

import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductService productService;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productService);
    }

    @Test
    void getAllCategoriesReturnsSortedResponseDtos() {
        Category cakes = category(1L, "Cakes", "Cake desc", "cakes.jpg");
        Category bread = category(2L, "Bread", "Bread desc", "bread.jpg");
        when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")))
                .thenReturn(List.of(bread, cakes));

        List<CategoryResponseDto> categories = categoryService.getAllCategories();

        assertThat(categories).hasSize(2);
        assertThat(categories.get(0).getName()).isEqualTo("Bread");
        assertThat(categories.get(1).getName()).isEqualTo("Cakes");
    }

    @Test
    void getCategoryByIdReturnsCategoryWhenFound() {
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category(1L, "Cakes", "Cake desc", "cakes.jpg")));

        CategoryResponseDto response = categoryService.getCategoryById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Cakes");
        assertThat(response.getDescription()).isEqualTo("Cake desc");
        assertThat(response.getImageUrl()).isEqualTo("cakes.jpg");
    }

    @Test
    void getCategoryByIdThrowsWhenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found");
    }

    @Test
    void createCategoryTrimsNameAndSavesCategory() {
        CategoryRequestDto request = request(" Cakes ", "Cake desc", "cakes.jpg");
        when(categoryRepository.existsByNameIgnoreCase("Cakes")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(1L);
            return category;
        });

        CategoryResponseDto response = categoryService.createCategory(request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Cakes");
        assertThat(response.getName()).isEqualTo("Cakes");
    }

    @Test
    void createCategoryRejectsDuplicateName() {
        CategoryRequestDto request = request("Cakes", "Cake desc", "cakes.jpg");
        when(categoryRepository.existsByNameIgnoreCase("Cakes")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Category already exists");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategoryUpdatesExistingCategory() {
        Category existing = category(1L, "Old", "Old desc", "old.jpg");
        CategoryRequestDto request = request("New", "New desc", "new.jpg");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("New", 1L)).thenReturn(false);
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponseDto response = categoryService.updateCategory(1L, request);

        assertThat(response.getName()).isEqualTo("New");
        assertThat(response.getDescription()).isEqualTo("New desc");
        assertThat(response.getImageUrl()).isEqualTo("new.jpg");
        verify(categoryRepository).save(existing);
    }

    @Test
    void updateCategoryRejectsDuplicateNameFromAnotherCategory() {
        Category existing = category(1L, "Old", "Old desc", "old.jpg");
        CategoryRequestDto request = request("Cakes", "Cake desc", "cakes.jpg");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Cakes", 1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Category already exists");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategoryDeletesWhenNoProductsUseIt() {
        Category existing = category(1L, "Cakes", "Cake desc", "cakes.jpg");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productService.existsByCategory(1L)).thenReturn(false);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void deleteCategoryRejectsWhenProductsUseIt() {
        Category existing = category(1L, "Cakes", "Cake desc", "cakes.jpg");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productService.existsByCategory(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot delete category because products are assigned to it");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryThrowsWhenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found");
    }

    private Category category(Long id, String name, String description, String imageUrl) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDescription(description);
        category.setImageUrl(imageUrl);
        return category;
    }

    private CategoryRequestDto request(String name, String description, String imageUrl) {
        CategoryRequestDto request = new CategoryRequestDto();
        request.setName(name);
        request.setDescription(description);
        request.setImageUrl(imageUrl);
        return request;
    }
}
