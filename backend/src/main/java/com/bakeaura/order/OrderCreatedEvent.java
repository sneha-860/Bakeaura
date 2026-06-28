package com.bakeaura.order;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    private final Order order;
    private final String customerEmail;
    private final String referralCode;

    public OrderCreatedEvent(Object source, Order order,
                             String customerEmail, String referralCode) {
        super(source);
        this.order = order;
        this.customerEmail = customerEmail;
        this.referralCode = referralCode;
    }
}
