-- Старые таски RECEIPT_EMAIL отправлялись без productCode в payload.
-- Восстанавливаем тип продукта по оплатам клиента (Юкасса, SUCCEEDED):
--   все оплаты по 1490 руб -> GUIDE;
--   1490 нет вообще и у всех оплат один код продукта -> этот код (COURSE / COURSE_PERSONAL);
--   смешанные суммы / коды -> не трогаем, останется сверка только по email.
WITH legacy AS (
    SELECT id, lower(trim(payload::jsonb ->> 'to')) AS email
    FROM task
    WHERE type = 'RECEIPT_EMAIL'
      AND payload LIKE '{%'
      AND COALESCE(payload::jsonb ->> 'productCode', '') = ''
      AND COALESCE(trim(payload::jsonb ->> 'to'), '') <> ''
), tx AS (
    SELECT lower(trim(email)) AS email,
           CASE
               WHEN bool_and(amount = 149000) THEN 'GUIDE'
               WHEN bool_or(amount = 149000) THEN NULL
               WHEN count(DISTINCT product_code) = 1 THEN min(product_code)
               ELSE NULL
           END AS resolved_code
    FROM payment_transaction
    WHERE provider = 'YOOKASSA'
      AND status = 'SUCCEEDED'
      AND product_code IN ('GUIDE', 'COURSE', 'COURSE_PERSONAL')
      AND COALESCE(trim(email), '') <> ''
    GROUP BY lower(trim(email))
)
UPDATE task t
SET payload = jsonb_set(t.payload::jsonb, '{productCode}', to_jsonb(tx.resolved_code))::text
FROM legacy l
JOIN tx ON tx.email = l.email
WHERE t.id = l.id
  AND tx.resolved_code IS NOT NULL;
