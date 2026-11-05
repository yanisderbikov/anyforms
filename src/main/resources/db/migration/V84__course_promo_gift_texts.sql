UPDATE promo_popup
SET title = 'на курс «Научись делать молды сам»',
    updated_at = now()
WHERE id = '5c1f3b2e-7d4a-4c6b-9e8f-0a1b2c3d4e5f'
  AND title = 'скидка {discount} на курс по молдам';

UPDATE promo_popup
SET description = 'Ровно столько, сколько вы заплатили за заказ.',
    updated_at = now()
WHERE id = '5c1f3b2e-7d4a-4c6b-9e8f-0a1b2c3d4e5f'
  AND description = 'Хотите делать такие формы сами? Вернём стоимость этой покупки скидкой на курс по производству силиконовых форм: вместо {oldPrice} курс обойдётся в {price}. Промокод действует {days}.';
