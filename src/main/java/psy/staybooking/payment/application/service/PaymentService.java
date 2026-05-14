package psy.staybooking.payment.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.Payment;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProcessor paymentProcessor;
    private final PaymentTransactionService paymentTransactionService;

    public Payment processPayment(PaymentCreateDto request) {
        Payment payment = paymentTransactionService.createPayment(request);
        if (payment.isPointOnly()) {
            return payment;
        }

        PaymentResponseDto paymentResponse = paymentProcessor.processPayment(payment, request);
        return paymentTransactionService.completePayment(payment.getPaymentId(), paymentResponse);
    }
}
