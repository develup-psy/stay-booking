CREATE TABLE products
(
    product_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    price_amount    BIGINT       NOT NULL,
    total_stock     INT          NOT NULL,
    sale_open_at    DATETIME(3)  NOT NULL,
    sale_close_at   DATETIME(3)  NULL,
    check_in_at     DATETIME(3)  NOT NULL,
    check_out_at    DATETIME(3)  NOT NULL,
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    CONSTRAINT uk_products_product_code UNIQUE (product_code)
);

CREATE TABLE bookings
(
    booking_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_no         VARCHAR(40) NOT NULL,
    user_id            BIGINT      NOT NULL,
    product_id         BIGINT      NOT NULL,
    checkout_token_id  VARCHAR(64) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    booked_price_amount BIGINT     NOT NULL,
    failure_code       VARCHAR(50) NULL,
    confirmed_at       DATETIME(3) NULL,
    failed_at          DATETIME(3) NULL,
    created_at         DATETIME(3) NOT NULL,
    updated_at         DATETIME(3) NOT NULL,
    CONSTRAINT uk_bookings_booking_no UNIQUE (booking_no),
    CONSTRAINT uk_bookings_checkout_token_id UNIQUE (checkout_token_id)
);

CREATE INDEX idx_bookings_product_status_created ON bookings (product_id, status, created_at);
CREATE INDEX idx_bookings_status_created ON bookings (status, created_at);

CREATE TABLE payments
(
    payment_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id          BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL,
    total_amount        BIGINT      NOT NULL,
    point_amount        BIGINT      NOT NULL,
    external_method     VARCHAR(20) NULL,
    external_amount     BIGINT      NOT NULL,
    provider_transaction_id VARCHAR(100) NULL,
    approved_at         DATETIME(3) NULL,
    last_error_code     VARCHAR(50) NULL,
    last_error_message  VARCHAR(255) NULL,
    created_at          DATETIME(3) NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    CONSTRAINT uk_payments_booking_id UNIQUE (booking_id)
);

CREATE TABLE payment_logs
(
    payment_log_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id          BIGINT       NOT NULL,
    event_type          VARCHAR(30)  NOT NULL,
    event_message       VARCHAR(255) NULL,
    created_at          DATETIME(3)  NOT NULL,
    updated_at          DATETIME(3)  NOT NULL,
    CONSTRAINT fk_payment_logs_payment FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
);

CREATE INDEX idx_payment_logs_payment_created ON payment_logs (payment_id, created_at);

CREATE TABLE point_wallets
(
    user_id       BIGINT      PRIMARY KEY,
    total_amount  BIGINT      NOT NULL,
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL
);

CREATE TABLE point_holds
(
    point_hold_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    booking_id    BIGINT      NOT NULL,
    amount        BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL,
    CONSTRAINT uk_point_holds_booking_id UNIQUE (booking_id),
    CONSTRAINT fk_point_holds_wallet FOREIGN KEY (user_id) REFERENCES point_wallets (user_id)
);

CREATE INDEX idx_point_holds_status_created ON point_holds (status, created_at);
CREATE INDEX idx_point_holds_user_status ON point_holds (user_id, status);

CREATE TABLE system_modes
(
    system_mode_id TINYINT      PRIMARY KEY,
    mode           VARCHAR(20)  NOT NULL,
    reason         VARCHAR(255) NULL,
    updated_by     VARCHAR(50)  NOT NULL,
    updated_at     DATETIME(3)  NOT NULL
);
