package com.bakeaura.product;

import com.bakeaura.category.CategoryRepository;
import com.bakeaura.category.Category;
import com.bakeaura.enums.Role;
import com.bakeaura.exception.BadRequestException;
import com.bakeaura.exception.ResourceNotFoundException;
import com.bakeaura.order.OrderItemRepository;
// CategoryRepository and OrderItemRepository are injected directly (cross-module) because
// the dependency graph prevents clean delegation: CategoryService → ProductService and
// OrderService → ProductService both create circular deps if reversed.
// These are narrow, read-only FK lookups — no business logic from those modules is invoked.
import org.springframework.security.access.AccessDeniedException;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;

    @CacheEvict(value = {"products", "sellerList"}, allEntries = true)
    public ProductDto createProduct(ProductCreateDto dto, Long userId) {
        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (seller.getRole() != Role.SELLER) {
            throw new BadRequestException("Only sellers can create products");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (Boolean.TRUE.equals(dto.getIsPreOrderOnly()) &&
                (dto.getMinAdvanceDays() == null || dto.getMinAdvanceDays() < 1)) {
            throw new BadRequestException(
                    "Pre-order-only products must specify a minimum advance booking of at least 1 day");
        }

        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setSeller(seller);
        product.setCategory(category);
        product.setImageUrl(dto.getImageUrl());
        product.setIsAvailable(true);
        product.setIsPreOrderOnly(Boolean.TRUE.equals(dto.getIsPreOrderOnly()));
        product.setMinAdvanceDays(dto.getMinAdvanceDays());

        return toDto(productRepository.save(product));
    }

    @Cacheable(value = "products", key = "'all'")
    public List<ProductDto> getAllProducts() {
        return productRepository.findByIsAvailableTrue(PageRequest.of(0, 200)).stream()
                .map(this::toDto)
                .toList();
    }

    @Cacheable(value = "products", key = "#id")
    public ProductDto getProductById(Long id) {
        return productRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
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
        return productRepository.findByNameContainingIgnoreCase(keyword, PageRequest.of(0, 100)).stream()
                .map(this::toDto)
                .toList();
    }

    @CacheEvict(value = {"products", "sellerList"}, allEntries = true)
    public ProductDto updateProduct(Long id, ProductCreateDto dto, Long userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new AccessDeniedException("You can update only your own products");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (Boolean.TRUE.equals(dto.getIsPreOrderOnly()) &&
                (dto.getMinAdvanceDays() == null || dto.getMinAdvanceDays() < 1)) {
            throw new BadRequestException(
                    "Pre-order-only products must specify a minimum advance booking of at least 1 day");
        }

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setCategory(category);
        product.setImageUrl(dto.getImageUrl());
        product.setIsPreOrderOnly(Boolean.TRUE.equals(dto.getIsPreOrderOnly()));
        product.setMinAdvanceDays(dto.getMinAdvanceDays());

        return toDto(productRepository.save(product));
    }

    @CacheEvict(value = {"products", "sellerList"}, allEntries = true)
    public void deleteProduct(Long id, Long userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new AccessDeniedException("You can delete only your own products");
        }

        if (orderItemRepository.existsByProduct_Id(id)) {
            throw new BadRequestException(
                    "This product has order history and cannot be deleted. " +
                    "Use 'Toggle availability' to hide it from customers instead.");
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
            if (available != null) {
                predicates.add(cb.equal(root.get("isAvailable"), available));
            }

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

    public long countProductsBySeller(Long sellerId) {
        return productRepository.countBySellerId(sellerId);
    }

    public Map<Long, Long> countProductsBySellerIds(Collection<Long> sellerIds) {
        if (sellerIds.isEmpty()) return Map.of();
        return productRepository.countBySellerIds(sellerIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    public boolean existsByCategory(Long categoryId) {
        return productRepository.existsByCategoryId(categoryId);
    }

    @CacheEvict(value = {"products", "sellerList"}, allEntries = true)
    public ProductDto toggleAvailability(Long productId, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User seller = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new AccessDeniedException("You can only modify your own products");
        }

        product.setIsAvailable(!product.getIsAvailable());
        return toDto(productRepository.save(product));
    }

    public ProductDto toDto(Product product) {
        User seller = product.getSeller();
        Category category = product.getCategory();
        String displayName = seller != null ? seller.getName() : null;
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getImageUrl(),
                product.getIsAvailable(),
                product.getIsPreOrderOnly(),
                product.getMinAdvanceDays(),
                seller == null ? null : seller.getId(),
                displayName,
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                product.getCreatedAt()
        );
    }
}