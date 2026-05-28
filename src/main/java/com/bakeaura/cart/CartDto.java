package com.bakeaura.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartDto implements Serializable {
    private String userEmail;
    private List<CartItemDto> items = new ArrayList<>();

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(CartItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
