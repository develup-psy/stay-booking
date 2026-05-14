package psy.staybooking.payment.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import psy.staybooking.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void createCreatesPointOnlyPayment() {
        Payment payment = Payment.create(1L, 80_000L, 80_000L, null, 0L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.isPointOnly()).isTrue();
        assertThat(payment.getExternalMethod()).isNull();
        assertThat(payment.getLogs()).hasSize(1);
        assertThat(payment.getLogs().getFirst().getEventType()).isEqualTo(PaymentEventType.CREATED);
    }

    @Test
    void createThrowsWhenExternalMethodIsMissing() {
        assertThatThrownBy(() -> Payment.create(1L, 100_000L, 20_000L, null, 80_000L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void markSucceededChangesPointOnlyPaymentStatus() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD, 80_000L);

        assertThatThrownBy(payment::markSucceeded).isInstanceOf(BusinessException.class);
    }

    @Test
    void markSucceededCompletesPointOnlyPayment() {
        Payment payment = Payment.create(1L, 80_000L, 80_000L, null, 0L);

        payment.markSucceeded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void startProcessingAddsApprovalRequestedLog() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD, 80_000L);
        payment.startProcessing();

        assertThat(payment.getLogs()).hasSize(2);
        assertThat(payment.getLogs().getLast().getEventType()).isEqualTo(PaymentEventType.APPROVAL_REQUESTED);
    }

    @Test
    void approveChangesPaymentStatusAndAddsLog() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD, 80_000L);
        payment.startProcessing();
        payment.approve("pg-tx-1", LocalDateTime.of(2026, 5, 14, 11, 1));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("pg-tx-1");
        assertThat(payment.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 5, 14, 11, 1));
        assertThat(payment.getLogs().getLast().getEventType()).isEqualTo(PaymentEventType.SUCCEEDED);
    }

    @Test
    void failChangesPaymentStatusAndAddsLog() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD, 80_000L);
        payment.startProcessing();
        payment.fail("DECLINED", "card declined");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getLastErrorCode()).isEqualTo("DECLINED");
        assertThat(payment.getLogs().getLast().getEventType()).isEqualTo(PaymentEventType.FAILED);
        assertThat(payment.getLogs().getLast().getEventMessage()).isEqualTo("card declined");
    }

    @Test
    void approveWorksWithoutSeparateAttemptEntity() {
        Payment payment = Payment.create(1L, 100_000L, 20_000L, ExternalPaymentMethod.CARD, 80_000L);
        payment.approve("pg-tx-1", LocalDateTime.of(2026, 5, 14, 11, 1));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getLogs().getLast().getEventType()).isEqualTo(PaymentEventType.SUCCEEDED);
    }
}
