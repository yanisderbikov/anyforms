ALTER TABLE users
    ADD COLUMN shop_id UUID REFERENCES shop (id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_users_shop_id ON users (shop_id);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
        CHECK (role IN ('ADMIN', 'SALES_MANAGER', 'PROJECT_MANAGER', 'SHOP_OWNER'));

ALTER TABLE users
    ADD CONSTRAINT users_shop_owner_has_shop
        CHECK (role <> 'SHOP_OWNER' OR shop_id IS NOT NULL);
