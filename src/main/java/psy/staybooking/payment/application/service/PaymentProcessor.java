package psy.staybooking.payment.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import psy.staybooking.common.exception.BusinessException;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.PaymentContext;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.domain.Payment;

@Service
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentStrategyFactory paymentStrategyFactory;

    public PaymentResponseDto processPayment(Payment payment, PaymentCreateDto request) {
        try {
            PaymentContext paymentContext = PaymentContext.from(payment, request);
            PaymentStrategy paymentStrategy = paymentStrategyFactory.getStrategy(paymentContext);
            return paymentStrategy.confirmPayment(paymentContext);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.PAYMENT_APPROVAL_FAILED) {
                return PaymentResponseDto.approvalFailed(null, exception.getMessage());
            }
            if (exception.getErrorCode() == ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE) {
                return PaymentResponseDto.providerUnavailable(null, exception.getMessage());
            }
            return PaymentResponseDto.processingFailed(exception.getMessage());
        } catch (RuntimeException exception) {
            return PaymentResponseDto.providerUnavailable(null, "외부 결제 연동에 실패했습니다.");
        }
    }
}
