package psy.staybooking.payment.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.Payment;

@Service
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentStrategyFactory paymentStrategyFactory;

    public PaymentResponseDto processPayment(Payment payment, PaymentCreateDto request) {
        PaymentContext paymentContext = PaymentContext.from(payment, request);
        PaymentStrategy paymentStrategy = paymentStrategyFactory.getStrategy(paymentContext);
        return paymentStrategy.confirmPayment(paymentContext);
    }
}
