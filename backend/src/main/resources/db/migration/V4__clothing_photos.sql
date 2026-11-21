-- Replace the clothing illustrations with real product photography (Unsplash, see frontend/public/products/CREDITS.md)
-- and align names/descriptions with what each photo shows.

UPDATE products SET image_url = '/products/everyday-cotton-tee.webp',
    description = 'Soft 100% organic cotton crew-neck tee with a relaxed fit. Pre-shrunk and built for daily wear.'
WHERE sku = 'AP-TEE-003';

UPDATE products SET name = 'Essential Navy Tee', image_url = '/products/essential-navy-tee.webp',
    description = 'Midweight cotton crew-neck tee in deep navy with a clean, modern fit.'
WHERE sku = 'AP-TEE-004';

UPDATE products SET name = 'Heather Grey Fleece Hoodie', image_url = '/products/grey-fleece-hoodie.webp',
    description = 'Heavyweight brushed fleece hoodie with a roomy hood, kangaroo pocket and dropped shoulders.'
WHERE sku = 'AP-HOOD-005';

UPDATE products SET image_url = '/products/slim-stretch-jeans.webp',
    description = 'Mid-wash five-pocket denim with a touch of stretch and a slim, tapered leg.'
WHERE sku = 'AP-JEAN-006';

UPDATE products SET name = 'Drawstring Chino Shorts', price = 34.50, image_url = '/products/chino-shorts.webp',
    description = 'Lightweight cotton shorts with an elastic drawstring waist, in navy, sand and off-white.'
WHERE sku = 'AP-JOGG-007';

UPDATE products SET name = 'Linen Button-Down Shirt', image_url = '/products/linen-shirt.webp',
    description = 'Long-sleeve washed linen shirt in chambray blue: breathable, relaxed and easy to layer.'
WHERE sku = 'AP-SHRT-008';

UPDATE products SET name = 'White Lace Summer Dress', image_url = '/products/lace-summer-dress.webp',
    description = 'Sleeveless dress with a delicate lace bodice and a light, flowing tulle skirt.'
WHERE sku = 'AP-DRSS-009';

UPDATE products SET name = 'Washed Cotton Cap', image_url = '/products/washed-cotton-cap.webp',
    description = 'Vintage-washed six-panel cotton cap with a curved brim and adjustable strap.'
WHERE sku = 'AP-CAP-010';

UPDATE products SET name = 'Ribbed Crew Socks (3-pack)', image_url = '/products/ribbed-crew-socks.webp',
    description = 'Soft textured-knit crew socks in stone, orchid and mustard.'
WHERE sku = 'AP-SOCK-011';

UPDATE products SET name = 'Packable Rain Jacket', image_url = '/products/packable-rain-jacket.webp',
    description = 'Waterproof, breathable shell with a storm hood; packs into its own pocket.'
WHERE sku = 'AP-JCKT-002';

UPDATE products SET name = 'Classic White Leather Sneakers', image_url = '/products/white-leather-sneakers.webp',
    description = 'Minimal low-top leather sneakers with a cushioned insole for all-day comfort.'
WHERE sku = 'AP-RUNS-001';
