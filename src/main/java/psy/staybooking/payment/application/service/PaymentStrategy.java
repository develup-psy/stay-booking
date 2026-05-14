package psy.staybooking.payment.application.service;

import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.payment.application.dto.PaymentResponseDto;

public interface PaymentStrategy {

    boolean supports(PaymentContext paymentContext);

    PaymentResponseDto confirmPayment(PaymentContext paymentContext);
}
