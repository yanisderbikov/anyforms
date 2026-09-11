CREATE TABLE cdek_pvz (
    code         VARCHAR(64) PRIMARY KEY,
    name         VARCHAR,
    country_code VARCHAR(8),
    region       VARCHAR,
    city         VARCHAR,
    postal_code  VARCHAR(16),
    address      VARCHAR,
    address_full VARCHAR,
    work_time    VARCHAR,
    longitude    DOUBLE PRECISION,
    latitude     DOUBLE PRECISION,
    synced_at    TIMESTAMP NOT NULL
);
