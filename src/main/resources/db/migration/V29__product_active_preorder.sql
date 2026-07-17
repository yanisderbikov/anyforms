-- Управление витриной: active — товар доступен к продаже, preorder — товар продаётся по предзаказу.
ALTER TABLE product ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product ADD COLUMN preorder BOOLEAN NOT NULL DEFAULT FALSE;
