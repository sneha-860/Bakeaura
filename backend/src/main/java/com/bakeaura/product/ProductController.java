package com.bakeaura.product;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDto>>> getAllProducts() {
        List<ProductDto> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.ok("Products fetched", products));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductDto>>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(
                ApiResponse.ok("Results", productService.searchProducts(keyword))
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(
            @Valid @RequestBody ProductCreateDto dto,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        ProductDto product = productService.createProduct(dto, userId);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok("Product created", product));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Found", productService.getProductById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductCreateDto dto,
            Authentication auth) {
        ProductDto product = productService.updateProduct(id, dto, Long.parseLong(auth.getName()));
        return ResponseEntity.ok(ApiResponse.ok("Product updated", product));
    }

    @PatchMapping("/{id}/toggle-available")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductDto>> toggleAvailable(
            @PathVariable Long id,
            Authentication auth) {
        ProductDto product = productService.toggleAvailability(id, Long.parseLong(auth.getName()));
        return ResponseEntity.ok(ApiResponse.ok("Product availability toggled", product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            Authentication auth) {
        productService.deleteProduct(id, Long.parseLong(auth.getName()));
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<Page<ProductDto>>> filterProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Products fetched",
                productService.filterProducts(keyword, categoryId, sellerId, minPrice, maxPrice, available, pageable)
        ));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.ok("Products fetched", productService.getProductsByCategory(categoryId)));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getBySeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.ok("Products fetched", productService.getProductsBySeller(sellerId)));
    }
}