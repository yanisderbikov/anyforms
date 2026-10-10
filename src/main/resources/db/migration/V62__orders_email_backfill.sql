UPDATE orders o
SET email = pt.email
FROM (SELECT DISTINCT ON (order_id) order_id, btrim(email) AS email
      FROM payment_transaction
      WHERE order_id IS NOT NULL
        AND btrim(email) <> ''
      ORDER BY order_id, created_at DESC NULLS LAST) pt
WHERE o.id = pt.order_id
  AND (o.email IS NULL OR btrim(o.email) = '');
