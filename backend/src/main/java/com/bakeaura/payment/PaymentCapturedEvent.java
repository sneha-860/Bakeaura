package com.bakeaura.payment;

import com.bakeaura.order.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentCapturedEvent extends ApplicationEvent {

    private final Order order;

    public PaymentCapturedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}
