package com.bakeaura.category;

import com.bakeaura.auth.JwtUtil;
import com.bakeaura.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, CategoryControllerTest.MethodSecurityTestConfig.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }

    @Test
    @WithAnonymousUser
    void anonymousCanGetAllCategories() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of(
                new CategoryResponseDto(1L, "Bread", "Bread desc", "bread.jpg")
        ));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Categories fetched"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Bread"));

        verify(categoryService).getAllCategories();
    }

    @Test
    @WithAnonymousUser
    void anonymousCanGetCategoryById() throws Exception {
        when(categoryService.getCategoryById(1L))
                .thenReturn(new CategoryResponseDto(1L, "Cakes", "Cake desc", "cakes.jpg"));

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category fetched"))
                .andExpect(jsonPath("$.data.name").value("Cakes"));

        verify(categoryService).getCategoryById(1L);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanCreateCategory() throws Exception {
        when(categoryService.createCategory(any(CategoryRequestDto.class)))
                .thenReturn(new CategoryResponseDto(1L, "Cakes", "Cake desc", "cakes.jpg"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cakes",
                                  "description": "Cake desc",
                                  "imageUrl": "cakes.jpg"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category created"))
                .andExpect(jsonPath("$.data.name").value("Cakes"));

        verify(categoryService).createCategory(any(CategoryRequestDto.class));
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void customerCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cakes"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(categoryService);
    }

    @Test
    @WithMockUser(username = "seller@example.com", roles = "SELLER")
    void sellerCannotUpdateCategory() throws Exception {
        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cakes"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(categoryService);
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(categoryService);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanUpdateCategory() throws Exception {
        when(categoryService.updateCategory(eq(1L), any(CategoryRequestDto.class)))
                .thenReturn(new CategoryResponseDto(1L, "Updated", "Updated desc", "updated.jpg"));

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "description": "Updated desc",
                                  "imageUrl": "updated.jpg"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category updated"))
                .andExpect(jsonPath("$.data.name").value("Updated"));

        verify(categoryService).updateCategory(eq(1L), any(CategoryRequestDto.class));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category deleted"));

        verify(categoryService).deleteCategory(1L);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void createCategoryRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(categoryService);
    }
}
