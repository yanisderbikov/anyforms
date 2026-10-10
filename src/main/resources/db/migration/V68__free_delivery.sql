CREATE TABLE delivery_settings (
    id                              SMALLINT PRIMARY KEY CHECK (id = 1),
    free_delivery_enabled           BOOLEAN NOT NULL,
    free_delivery_threshold_kopecks BIGINT  NOT NULL CHECK (free_delivery_threshold_kopecks > 0),
    updated_at                      TIMESTAMP(6) WITH TIME ZONE
);

INSERT INTO delivery_settings (id, free_delivery_enabled, free_delivery_threshold_kopecks, updated_at)
VALUES (1, TRUE, 1200000, now());

ALTER TABLE orders ADD COLUMN free_delivery BOOLEAN NOT NULL DEFAULT FALSE;
