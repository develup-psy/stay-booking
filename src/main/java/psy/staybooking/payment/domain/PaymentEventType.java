package psy.staybooking.payment.domain;

public enum PaymentEventType {
    CREATED,
    APPROVAL_REQUESTED,
    APPROVAL_SUCCEEDED,
    APPROVAL_FAILED,
    SUCCEEDED,
    FAILED
}
