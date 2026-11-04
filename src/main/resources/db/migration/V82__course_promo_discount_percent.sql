UPDATE promo_popup
SET discount_percent = 100
WHERE popup_type = 'COURSE_AFTER_PURCHASE'
  AND discount_percent = 0
  AND discount_amount_kopecks IS NULL;
