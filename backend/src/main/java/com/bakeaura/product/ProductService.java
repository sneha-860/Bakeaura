package com.bakeaura.product;

import com.bakeaura.category.CategoryRepository;
import com.bakeaura.category.Category;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @CacheEvict(value = "products", allEntries = true)
    public ProductDto createProduct(ProductCreateDto dto, Long userId) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new RuntimeException("Only sellers can create products");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setSeller(seller);
        product.setCategory(category);
        product.setImageUrl(dto.getImageUrl());
        product.setIsAvailable(true);

        return toDto(productRepository.save(product));
    }

    @Cacheable(value = "products", key = "'all'")
    public List<ProductDto> getAllProducts() {
        return productRepository.findByIsAvailableTrue().stream()
                .map(this::toDto)
                .toList();
    }

    @Cacheable(value = "products", key = "#id")
    public Optional<ProductDto> getProductById(Long id) {
        return productRepository.findById(id).map(this::toDto);
    }

    public Product getProductEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public void saveProduct(Product product) {
        productRepository.save(product);
    }

    @Cacheable(value = "products", key = "'search:' + #keyword")
    public List<ProductDto> searchProducts(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(this::toDto)
                .toList();
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductDto updateProduct(Long id, ProductCreateDto dto, Long userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("You can update only your own products");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setCategory(category);
        product.setImageUrl(dto.getImageUrl());

        return toDto(productRepository.save(product));
    }

    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id, Long userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("You can delete only your own products");
        }

        productRepository.delete(product);
    }

    public Page<ProductDto> filterProducts(
            String keyword,
            Long categoryId,
            Long sellerId,
            java.math.BigDecimal minPrice,
            java.math.BigDecimal maxPrice,
            Boolean available,
            Pageable pageable
    ) {
        return productRepository.findAll((root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (sellerId != null) {
                predicates.add(cb.equal(root.get("seller").get("id"), sellerId));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            predicates.add(cb.equal(root.get("isAvailable"), available == null || available));

            return cb.and(predicates.toArray(Predicate[]::new));
        }, pageable).map(this::toDto);
    }

    public List<ProductDto> getProductsBySeller(Long sellerId) {
        return productRepository.findBySellerId(sellerId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<ProductDto> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId).stream()
                .map(this::toDto)
                .toList();
    }

    public long countProducts() {
        return productRepository.count();
    }

    public boolean existsByCategory(Long categoryId) {
        return productRepository.existsByCategoryId(categoryId);
    }

    public ProductDto toDto(Product product) {
        User seller = product.getSeller();
        Category category = product.getCategory();
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getImageUrl(),
                product.getIsAvailable(),
                seller == null ? null : seller.getId(),
                seller == null ? null : seller.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getCreatedAt()
        );
    }
}