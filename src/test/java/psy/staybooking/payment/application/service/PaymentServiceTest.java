package psy.staybooking.payment.application.service;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import psy.staybooking.payment.application.dto.PaymentCreateDto;
import psy.staybooking.payment.application.dto.PaymentResponseDto;
import psy.staybooking.payment.application.dto.CardPaymentDetailDto;
import psy.staybooking.payment.domain.ExternalPaymentMethod;
import psy.staybooking.payment.domain.Payment;
import psy.staybooking.payment.domain.PaymentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentProcessor paymentProcessor;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPaymentCompletesPointOnlyPaymentWithoutProcessor() {
        Payment payment = Payment.create(1L, 50_000L, 50_000L, null);
        payment.markSucceeded();
        when(paymentTransactionService.createPayment(any(PaymentCreateDto.class))).thenReturn(payment);

        Payment result = paymentService.processPayment(
            PaymentCreateDto.builder()
                .bookingId(1L)
                .totalAmount(50_000L)
                .pointAmount(50_000L)
                .build()
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentProcessor, never()).processPayment(any(), any());
    }

    @Test
    void processPaymentApprovesExternalPayment() throws Exception {
        Payment pendingPayment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        pendingPayment.startProcessing();
        when(paymentTransactionService.createPayment(any(PaymentCreateDto.class))).thenReturn(pendingPayment);
        when(paymentProcessor.processPayment(any(Payment.class), any())).thenReturn(
            PaymentResponseDto.approved("mock-1", LocalDateTime.of(2026, 5, 14, 18, 0))
        );
        Payment approvedPayment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD);
        approvedPayment.startProcessing();
        approvedPayment.approve("mock-1", LocalDateTime.of(2026, 5, 14, 18, 0));
        when(paymentTransactionService.completePayment(eq(pendingPayment.getPaymentId()), any(PaymentResponseDto.class))).thenReturn(approvedPayment);

        Payment payment = paymentService.processPayment(
            PaymentCreateDto.builder()
                .bookingId(1L)
                .totalAmount(100_000L)
                .pointAmount(20_000L)
                .externalPaymentMethod(ExternalPaymentMethod.CARD)
                .paymentDetail(CardPaymentDetailDto.builder()
                    .paymentToken("card-token")
                    .installmentMonths(0)
                    .build())
                .build()
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("mock-1");
    }

    @Test
    void processPaymentFailsExternalPayment() throws Exception {
        Payment pendingPayment = Payment.create(1L, 100_000L, 0L, ExternalPaymentMethod.YPAY);
        pendingPayment.startProcessing();
        when(paymentTransactionService.createPayment(any(PaymentCreateDto.class))).thenReturn(pendingPayment);
        when(paymentProcessor.processPayment(any(Payment.class), any())).thenReturn(
            PaymentResponseDto.failed("MOCK_DECLINED", "모의 결제가 거절되었습니다.")
        );
        Payment failedPayment = Payment.create(1L, 100_000L, 0L, ExternalPaymentMethod.YPAY);
        failedPayment.startProcessing();
        failedPayment.fail("MOCK_DECLINED", "모의 결제가 거절되었습니다.");
        when(paymentTransactionService.completePayment(eq(pendingPayment.getPaymentId()), any(PaymentResponseDto.class))).thenReturn(failedPayment);

        Payment payment = paymentService.processPayment(
            PaymentCreateDto.builder()
                .bookingId(1L)
                .totalAmount(100_000L)
                .pointAmount(0L)
                .externalPaymentMethod(ExternalPaymentMethod.YPAY)
                .paymentDetail(psy.staybooking.payment.application.dto.YpayPaymentDetailDto.builder()
                    .authorizationToken("ypay-token")
                    .build())
                .build()
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getLastErrorCode()).isEqualTo("MOCK_DECLINED");
    }
}
