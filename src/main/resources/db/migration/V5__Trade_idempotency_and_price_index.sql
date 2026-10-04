ALTER TABLE trades ADD COLUMN client_order_id VARCHAR(64);
ALTER TABLE trades ADD CONSTRAINT unique_user_client_order UNIQUE (user_id, client_order_id);

CREATE INDEX idx_crypto_prices_pair_created ON crypto_prices (crypto_pair_id, created_at DESC);
