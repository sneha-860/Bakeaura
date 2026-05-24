package com.bakeaura.product;

import com.bakeaura.category.CategoryRepository;
import com.bakeaura.category.Category;
import com.bakeaura.enums.Role;
import com.bakeaura.user.User;
import com.bakeaura.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor  // Lombok: auto-generates constructor for final fields
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    // Create a product (only sellers can do this — enforced in controller)
    @CacheEvict(value = "products", allEntries = true)
    public Product createProduct(ProductCreateDto dto, String sellerEmail) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Business rule: only verified sellers can list products
        if (seller.getRole() != Role.SELLER) {
            throw new RuntimeException("Only sellers can create products");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setSeller(seller);
        product.setCategory(category);
        product.setIsAvailable(true);

        return productRepository.save(product);
        // save() does INSERT and returns the saved object with generated ID
    }

    @Cacheable(value = "products", key = "'all'")
    public List<Product> getAllProducts() {
        return productRepository.findByIsAvailableTrue();
    }

    @Cacheable(value = "products", key = "#id")
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Cacheable(value = "products", key = "'search:' + #keyword")
    public List<Product> searchProducts(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product updateProduct(Long id, ProductCreateDto dto, String sellerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("You can update only your own products");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStockQuantity(dto.getStockQuantity());
        product.setCategory(category);

        return productRepository.save(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id, String sellerEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("You can delete only your own products");
        }

        productRepository.delete(product);
    }
}
