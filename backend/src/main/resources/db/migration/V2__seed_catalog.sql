-- Starter catalog so a fresh environment has something to browse.

INSERT INTO categories (name, slug) VALUES
    ('Electronics', 'electronics'),
    ('Home & Kitchen', 'home-kitchen'),
    ('Outdoors', 'outdoors'),
    ('Apparel', 'apparel');

INSERT INTO products (sku, name, description, price, category_id, status, created_at, updated_at)
SELECT p.sku, p.name, p.description, p.price, c.id, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'EL-HEAD-001' AS sku, 'Swift Wireless Headphones' AS name,
           'Over-ear Bluetooth headphones with 40-hour battery life and fast charging.' AS description,
           89.99 AS price, 'electronics' AS slug
    UNION ALL SELECT 'EL-CHRG-002', 'Rapid 65W USB-C Charger',
           'Compact GaN charger that powers a laptop and phone at the same time.', 34.50, 'electronics'
    UNION ALL SELECT 'EL-SPKR-003', 'Pocket Bluetooth Speaker',
           'Water-resistant speaker with a punchy sound and a 12-hour battery.', 45.00, 'electronics'
    UNION ALL SELECT 'EL-WTCH-004', 'Sprint Fitness Watch',
           'Heart-rate, GPS, and sleep tracking in a lightweight case.', 129.00, 'electronics'
    UNION ALL SELECT 'HK-KETL-001', 'Quick-Boil Electric Kettle',
           '1.7 L stainless steel kettle with auto shut-off.', 39.95, 'home-kitchen'
    UNION ALL SELECT 'HK-PRES-002', 'Express Pressure Cooker',
           'Six-quart multicooker for weeknight dinners in minutes.', 99.00, 'home-kitchen'
    UNION ALL SELECT 'HK-KNIF-003', 'Chef Knife 8"',
           'High-carbon stainless steel blade with a balanced handle.', 54.25, 'home-kitchen'
    UNION ALL SELECT 'OD-BOTL-001', 'Insulated Trail Bottle',
           'Keeps drinks cold for 24 hours or hot for 12.', 24.00, 'outdoors'
    UNION ALL SELECT 'OD-PACK-002', 'Daypack 22L',
           'Lightweight pack with a laptop sleeve and rain cover.', 64.99, 'outdoors'
    UNION ALL SELECT 'OD-LAMP-003', 'Rechargeable Headlamp',
           '400-lumen headlamp with red night mode.', 29.99, 'outdoors'
    UNION ALL SELECT 'AP-RUNS-001', 'Velocity Running Shoes',
           'Responsive cushioning for daily miles.', 110.00, 'apparel'
    UNION ALL SELECT 'AP-JCKT-002', 'Packable Wind Jacket',
           'Folds into its own pocket; ideal for unpredictable weather.', 75.00, 'apparel'
) p
JOIN categories c ON c.slug = p.slug;

INSERT INTO inventory_items (product_id, on_hand, reserved, version)
SELECT id,
       CASE sku WHEN 'EL-WTCH-004' THEN 1 WHEN 'HK-PRES-002' THEN 0 ELSE 25 END,
       0, 0
FROM products;
