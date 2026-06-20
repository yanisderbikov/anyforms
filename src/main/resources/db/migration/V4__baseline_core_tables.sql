-- ============================================================================
--  V4 — baseline таблиц, которые раньше создавались автоматически через
--  hibernate ddl-auto=update (orders, order_items, users, product).
--  Теперь схема ведётся только миграциями, а Hibernate переведён в режим validate.
--  Колонки/типы соответствуют тому, что генерировал Hibernate из @Entity.
-- ============================================================================

CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(255) NOT NULL
);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    lead_id         BIGINT NOT NULL UNIQUE,
    contact_id      BIGINT,
    contact_name    VARCHAR(255),
    contact_phone   VARCHAR(255),
    tracker         VARCHAR(255),
    delivery_status VARCHAR(255),
    pvz_sdek        VARCHAR(255),
    pvz_sdek_city   VARCHAR(255),
    purchase_date   TIMESTAMP(6),
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6),
    retail          BOOLEAN,
    comment         VARCHAR(255),
    title           VARCHAR(255)
);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders (id),
    product_name VARCHAR(255) NOT NULL,
    quantity     INTEGER NOT NULL,
    product_id   BIGINT,
    catalog_id   BIGINT
);

CREATE TABLE product (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT NOT NULL,
    s3photos_folder_path  VARCHAR(255) NOT NULL,
    price                 VARCHAR(255) NOT NULL,
    crossed_price         VARCHAR(255),
    discount_percent      VARCHAR(255),
    tg_link               VARCHAR(255) NOT NULL,
    order_number          INTEGER
);
