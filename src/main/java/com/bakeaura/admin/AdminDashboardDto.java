package com.bakeaura.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminDashboardDto {
    private long users;
    private long products;
    private long orders;
    private long payments;
    private long categories;
}
