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

    public Payment createPayment(PaymentCreateDto request) {
        return paymentTransactionService.createPayment(request);
    }

    public PaymentResponseDto processExternalPayment(Payment payment, PaymentCreateDto request) {
        return paymentProcessor.processPayment(payment, request);
    }

    public Payment succeedPayment(Long paymentId, PaymentResponseDto paymentResponse) {
        return paymentTransactionService.succeedPayment(paymentId, paymentResponse);
    }

    public Payment failPayment(Long paymentId, PaymentResponseDto paymentResponse) {
        return paymentTransactionService.failPayment(paymentId, paymentResponse);
    }

    public Payment completePointOnlyPayment(Long paymentId) {
        return paymentTransactionService.completePointOnlyPayment(paymentId);
    }

    public Payment getPayment(Long paymentId) {
        return paymentTransactionService.getPayment(paymentId);
    }

    public Payment processPayment(PaymentCreateDto request) {
        Payment payment = paymentTransactionService.createPayment(request);
        if (payment.isPointOnly()) {
            return paymentTransactionService.completePointOnlyPayment(payment.getPaymentId());
        }

        PaymentResponseDto paymentResponse = paymentProcessor.processPayment(payment, request);
        if (paymentResponse.isApproved()) {
            return paymentTransactionService.succeedPayment(payment.getPaymentId(), paymentResponse);
        }
        return paymentTransactionService.failPayment(payment.getPaymentId(), paymentResponse);
    }
}
