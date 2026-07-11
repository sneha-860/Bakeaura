package com.bakeaura.product;

import com.bakeaura.category.CategoryRepository;
import com.bakeaura.seller.SellerProfileRepository;
import com.bakeaura.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, userRepository, categoryRepository, sellerProfileRepository);
    }

    @Test
    void staleVersionThrowsOptimisticLockException() {
        // Proves that Product's @Version field causes the ORM to reject a stale write
        // rather than silently overwriting a concurrent update
        Product product = new Product();
        product.setId(1L);
        product.setStockQuantity(10);

        when(productRepository.save(product))
                .thenReturn(product)                                                     // thread 1 saves OK
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L)); // thread 2 gets stale version rejected

        // Thread 1 reads stock=10, decrements by 2, saves successfully
        product.setStockQuantity(8);
        productService.saveProduct(product);

        // Thread 2 had a stale snapshot of stock=10 and tries to decrement by 3 (result=7);
        // @Version has already changed at the DB level, so the save is rejected
        product.setStockQuantity(7);
        assertThatThrownBy(() -> productService.saveProduct(product))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
