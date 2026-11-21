-- Clothing range for the Apparel category, with product illustrations served by the web app (/products/*.svg).

UPDATE categories SET name = 'Clothing & Apparel' WHERE slug = 'apparel';

UPDATE products SET image_url = '/products/shoes.svg'  WHERE sku = 'AP-RUNS-001';
UPDATE products SET image_url = '/products/jacket.svg' WHERE sku = 'AP-JCKT-002';

INSERT INTO products (sku, name, description, price, image_url, category_id, status, created_at, updated_at)
SELECT p.sku, p.name, p.description, p.price, p.image_url, c.id, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'AP-TEE-003' AS sku, 'Everyday Cotton T-Shirt' AS name,
           'Soft 100% organic cotton tee with a relaxed fit. Pre-shrunk and built for daily wear.' AS description,
           19.99 AS price, '/products/tee-navy.svg' AS image_url
    UNION ALL SELECT 'AP-TEE-004', 'Bolt Performance Tee',
           'Lightweight, quick-dry training tee with the Just Fast bolt print.', 24.99, '/products/tee-blue.svg'
    UNION ALL SELECT 'AP-HOOD-005', 'Midnight Fleece Hoodie',
           'Brushed fleece hoodie with a lined hood, kangaroo pocket and ribbed cuffs.', 54.00, '/products/hoodie.svg'
    UNION ALL SELECT 'AP-JEAN-006', 'Slim Fit Stretch Jeans',
           'Classic five-pocket denim with a touch of stretch for all-day comfort.', 59.95, '/products/jeans.svg'
    UNION ALL SELECT 'AP-JOGG-007', 'Travel Joggers',
           'Tapered joggers with an elastic waistband, drawcord and cuffed ankles.', 44.50, '/products/joggers.svg'
    UNION ALL SELECT 'AP-SHRT-008', 'Linen Button Shirt',
           'Breathable short-sleeve linen shirt for warm days.', 39.00, '/products/shirt-linen.svg'
    UNION ALL SELECT 'AP-DRSS-009', 'Summer Wrap Dress',
           'Flowing midi dress in a light, airy fabric with adjustable straps.', 64.00, '/products/dress.svg'
    UNION ALL SELECT 'AP-CAP-010', 'Classic Baseball Cap',
           'Six-panel cotton cap with a curved brim and adjustable strap.', 18.00, '/products/cap.svg'
    UNION ALL SELECT 'AP-SOCK-011', 'Running Socks (3-pack)',
           'Cushioned, moisture-wicking crew socks with arch support.', 14.99, '/products/socks.svg'
) p
JOIN categories c ON c.slug = 'apparel';

INSERT INTO inventory_items (product_id, on_hand, reserved, version)
SELECT id,
       CASE sku WHEN 'AP-DRSS-009' THEN 3 WHEN 'AP-CAP-010' THEN 40 ELSE 20 END,
       0, 0
FROM products
WHERE sku IN ('AP-TEE-003', 'AP-TEE-004', 'AP-HOOD-005', 'AP-JEAN-006', 'AP-JOGG-007', 'AP-SHRT-008',
              'AP-DRSS-009', 'AP-CAP-010', 'AP-SOCK-011');
