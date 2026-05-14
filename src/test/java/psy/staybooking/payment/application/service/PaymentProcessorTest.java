package psy.staybooking.payment.application.service;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.common.exception.ErrorCode;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.dto.YpayPaymentDetailDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    @Mock
    private PaymentApiClient paymentApiClient;

    @Test
    void processPaymentDelegatesToCardStrategy() {
        CardPaymentStrategy cardPaymentStrategy = new CardPaymentStrategy(paymentApiClient);
        YpayPaymentStrategy ypayPaymentStrategy = new YpayPaymentStrategy(paymentApiClient);
        PaymentStrategyFactory paymentStrategyFactory = new PaymentStrategyFactory(List.of(cardPaymentStrategy, ypayPaymentStrategy));
        PaymentProcessor paymentProcessor = new PaymentProcessor(paymentStrategyFactory);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        when(paymentApiClient.confirmCard(any(), any())).thenReturn(
            PaymentResponseDto.approved("pg-tx-1", LocalDateTime.of(2026, 5, 14, 21, 30))
        );

        PaymentResponseDto result = paymentProcessor.processPayment(
            payment,
            PaymentCreateDto.builder()
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-token")
                    .installmentMonths(0)
                    .build())
                .build()
        );

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getProviderTransactionId()).isEqualTo("pg-tx-1");
    }

    @Test
    void processPaymentReturnsProcessingFailedWhenStrategyDoesNotExist() {
        CardPaymentStrategy cardPaymentStrategy = new CardPaymentStrategy(paymentApiClient);
        PaymentStrategyFactory paymentStrategyFactory = new PaymentStrategyFactory(List.of(cardPaymentStrategy));
        PaymentProcessor paymentProcessor = new PaymentProcessor(paymentStrategyFactory);
        Payment payment = Payment.create(1L, 100_000L, 0L, ExternalPaymentMethod.YPAY);

        PaymentResponseDto result = paymentProcessor.processPayment(
            payment,
            PaymentCreateDto.builder()
                .paymentDetail(YpayPaymentDetailDto.builder()
                    .authorizationToken("ypay-token")
                    .build())
                .build()
        );

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_PROCESSING_FAILED.getCode());
    }

    @Test
    void processPaymentReturnsProviderUnavailableWhenStrategyReturnsTechnicalFailure() {
        CardPaymentStrategy cardPaymentStrategy = new CardPaymentStrategy(paymentApiClient);
        YpayPaymentStrategy ypayPaymentStrategy = new YpayPaymentStrategy(paymentApiClient);
        PaymentStrategyFactory paymentStrategyFactory = new PaymentStrategyFactory(List.of(cardPaymentStrategy, ypayPaymentStrategy));
        PaymentProcessor paymentProcessor = new PaymentProcessor(paymentStrategyFactory);
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        when(paymentApiClient.confirmCard(any(), any())).thenReturn(
            PaymentResponseDto.providerUnavailable("PROVIDER_UNAVAILABLE", "결제 연동 서버를 사용할 수 없습니다.")
        );

        PaymentResponseDto result = paymentProcessor.processPayment(
            payment,
            PaymentCreateDto.builder()
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("error-card-token")
                    .installmentMonths(0)
                    .build())
                .build()
        );

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE.getCode());
        assertThat(result.getProviderErrorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void processPaymentDelegatesToYpayStrategy() {
        CardPaymentStrategy cardPaymentStrategy = new CardPaymentStrategy(paymentApiClient);
        YpayPaymentStrategy ypayPaymentStrategy = new YpayPaymentStrategy(paymentApiClient);
        PaymentStrategyFactory paymentStrategyFactory = new PaymentStrategyFactory(List.of(cardPaymentStrategy, ypayPaymentStrategy));
        PaymentProcessor paymentProcessor = new PaymentProcessor(paymentStrategyFactory);
        Payment payment = Payment.create(1L, 100_000L, 10_000L, ExternalPaymentMethod.YPAY);
        when(paymentApiClient.confirmYpay(any(), any())).thenReturn(
            PaymentResponseDto.approvalFailed("PAYMENT_REJECTED", "결제가 거절되었습니다.")
        );

        PaymentResponseDto result = paymentProcessor.processPayment(
            payment,
            PaymentCreateDto.builder()
                .paymentDetail(YpayPaymentDetailDto.builder()
                    .authorizationToken("fail-ypay-token")
                    .build())
                .build()
        );

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_APPROVAL_FAILED.getCode());
        assertThat(result.getProviderErrorCode()).isEqualTo("PAYMENT_REJECTED");
    }
}
