package psy.staybooking.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import psy.staybooking.common.persistence.BaseTimeEntity;

@Getter
@Entity
@Table(name = "payment_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    private PaymentEventType eventType;

    private String eventMessage;

    @Builder(access = AccessLevel.PACKAGE)
    PaymentLog(Long paymentLogId, Payment payment, PaymentEventType eventType, String eventMessage) {
        this.paymentLogId = paymentLogId;
        this.payment = payment;
        this.eventType = eventType;
        this.eventMessage = eventMessage;
    }

    static PaymentLog create(Payment payment, PaymentEventType eventType, String eventMessage) {
        return PaymentLog.builder()
            .payment(payment)
            .eventType(eventType)
            .eventMessage(eventMessage)
            .build();
    }
}
