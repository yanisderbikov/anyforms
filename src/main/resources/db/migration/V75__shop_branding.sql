ALTER TABLE shop
    ADD COLUMN tagline        VARCHAR(255),
    ADD COLUMN description    TEXT,
    ADD COLUMN logo_url       VARCHAR(1024),
    ADD COLUMN logo_mark_url  VARCHAR(1024),
    ADD COLUMN hero_image_url VARCHAR(1024),
    ADD COLUMN hero_image_alt VARCHAR(255),
    ADD COLUMN hero_video_url VARCHAR(1024);

UPDATE shop
SET tagline        = 'Магазин молдов Дианы di_gips',
    description    = 'Диана — автор di_gips: молды, гипсовый декор и свечи ручной работы. Творчество, которое вдохновляет и объединяет',
    hero_image_url = 'https://storage.yandexcloud.net/anyforms/shop/di_gips/di_gips_hero.jpg',
    hero_image_alt = 'Диана, автор di_gips, со своими изделиями из гипса'
WHERE slug = 'di_gips';

UPDATE shop
SET tagline        = 'Магазин молдов lunasvecha',
    description    = 'Молды для свечей — подборка lunasvecha: лью свечи с 2020 года и обучаю свечеварению учеников по всему миру',
    logo_url       = '/lunasvecha_wordmark.svg',
    logo_mark_url  = '/lunasvecha_mark.svg',
    hero_video_url = 'https://storage.yandexcloud.net/anyforms/shop/lunasvecha/gypsum.webm'
WHERE slug = 'lunasvecha';

UPDATE shop
SET tagline        = 'Магазин молдов Анны Феликовой',
    description    = 'Профессиональные силиконовые молды для десертов — подборка Анны Феликовой',
    logo_url       = '/af_pastry_logo.png',
    hero_video_url = 'https://storage.yandexcloud.net/anyforms/shop/af_pastry_landing/af_pastry_1.webm'
WHERE slug = 'af_pastry';
