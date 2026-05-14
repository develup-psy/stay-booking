INSERT INTO products (
    product_id,
    product_code,
    name,
    price_amount,
    total_stock,
    sale_open_at,
    sale_close_at,
    check_in_at,
    check_out_at,
    created_at,
    updated_at
) VALUES (
    1,
    'SAMPLE-STAY-001',
    'seoul-sample-stay',
    120000,
    10,
    '2026-01-01 00:00:00.000',
    '2030-01-01 00:00:00.000',
    '2026-06-01 15:00:00.000',
    '2026-06-02 11:00:00.000',
    NOW(3),
    NOW(3)
)
ON DUPLICATE KEY UPDATE
    product_code = VALUES(product_code),
    name = VALUES(name),
    price_amount = VALUES(price_amount),
    total_stock = VALUES(total_stock),
    sale_open_at = VALUES(sale_open_at),
    sale_close_at = VALUES(sale_close_at),
    check_in_at = VALUES(check_in_at),
    check_out_at = VALUES(check_out_at),
    updated_at = NOW(3);

INSERT INTO point_wallets (
    user_id,
    total_amount,
    created_at,
    updated_at
) VALUES (
    1,
    50000,
    NOW(3),
    NOW(3)
)
ON DUPLICATE KEY UPDATE
    total_amount = VALUES(total_amount),
    updated_at = NOW(3);
