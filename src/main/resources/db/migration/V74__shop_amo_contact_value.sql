ALTER TABLE shop
    ADD COLUMN amo_contact_value VARCHAR(64);

UPDATE shop
SET amo_contact_value = slug
WHERE slug IN ('anyforms', 'di_gips', 'lunasvecha');
