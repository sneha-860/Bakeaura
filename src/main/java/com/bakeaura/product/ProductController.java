package com.bakeaura.product;

import com.bakeaura.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // PUBLIC: anyone can browse products (no token needed)
    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.ok("Products fetched", products));
    }
    // Postman: GET http://localhost:8080/api/products

    // PUBLIC: search
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Product>>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(
                ApiResponse.ok("Results", productService.searchProducts(keyword))
        );
    }
    // Postman: GET http://localhost:8080/api/products/search?keyword=cake

    // PROTECTED: only SELLER role can create products
    @PostMapping
    @PreAuthorize("hasRole('SELLER')")  // checks JWT role claim
    public ResponseEntity<ApiResponse<Product>> createProduct(
            @Valid @RequestBody ProductCreateDto dto,
            // @Valid triggers Validation annotations on the DTO
            Authentication auth) {
        // Spring injects the logged-in user's info here

        String email = auth.getName(); // gets email from JWT
        Product product = productService.createProduct(dto, email);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok("Product created", product));
    }
    // Postman: POST http://localhost:8080/api/products
    // Headers: Authorization: Bearer <your_token>
    // Body: { "name": "Chocolate Cake", "price": 499.00, ... }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(p -> ResponseEntity.ok(ApiResponse.ok("Found", p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Product>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductCreateDto dto,
            Authentication auth) {
        Product product = productService.updateProduct(id, dto, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Product updated", product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable Long id,
            Authentication auth) {
        productService.deleteProduct(id, auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }
}
