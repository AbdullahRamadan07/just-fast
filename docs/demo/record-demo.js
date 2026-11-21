// Records a narrated walkthrough of Just Fast running locally (docker compose) at http://localhost:8080.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = 'http://localhost:8080';
const OUT_DIR = process.argv[2];
const ADMIN = { email: 'admin@justfast.local', password: 'change-me-admin' }; // dev defaults from .env.example
const CUSTOMER = {
  name: 'Demo Shopper',
  email: `demo.${Date.now()}@justfast.local`,
  password: 'Demo-Sandbox-2026',
};

const pause = (page, ms = 1400) => page.waitForTimeout(ms);

async function caption(page, title, sub = '') {
  await page.evaluate(
    ([t, s]) => {
      let el = document.getElementById('jf-demo-caption');
      if (!el) {
        el = document.createElement('div');
        el.id = 'jf-demo-caption';
        el.style.cssText =
          'position:fixed;left:50%;bottom:28px;transform:translateX(-50%);z-index:99999;' +
          'background:rgba(3,23,63,.94);color:#fff;padding:14px 26px;border-radius:14px;' +
          'font:600 20px/1.35 system-ui,Segoe UI,sans-serif;box-shadow:0 10px 30px rgba(0,0,0,.25);' +
          'text-align:center;max-width:80vw;border-left:6px solid #006ffa;pointer-events:none';
        document.body.appendChild(el);
      }
      el.innerHTML = t + (s ? `<div style="font-weight:400;font-size:15px;opacity:.85;margin-top:4px">${s}</div>` : '');
    },
    [title, sub],
  );
}

async function type(locator, text) {
  await locator.click();
  await locator.pressSequentially(text, { delay: 35 });
}

async function smoothScroll(page, dy, steps = 8) {
  for (let i = 0; i < steps; i++) {
    await page.mouse.wheel(0, dy / steps);
    await page.waitForTimeout(90);
  }
}

(async () => {
  const browser = await chromium.launch({ slowMo: 120 });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    recordVideo: { dir: OUT_DIR, size: { width: 1280, height: 800 } },
  });
  const page = await context.newPage();

  // 1. Storefront
  await page.goto(BASE + '/');
  await page.getByText('Add to cart').first().waitFor();
  await caption(page, 'Just Fast storefront', 'Browse, search and filter a paginated catalog with live stock');
  await pause(page, 2600);
  await smoothScroll(page, 500);
  await pause(page, 1200);
  await smoothScroll(page, -500);

  // 2. Clothing category
  await page.getByRole('button', { name: 'Clothing & Apparel' }).click();
  await page.getByText('Heather Grey Fleece Hoodie').waitFor();
  await caption(page, 'New: Clothing & Apparel', 'Real product photography for the clothing range');
  await pause(page, 2200);
  await smoothScroll(page, 450);
  await pause(page, 1600);
  await smoothScroll(page, -450);

  // 3. Guests must sign in before adding to cart
  await page.locator('article', { hasText: 'Heather Grey Fleece Hoodie' }).getByRole('button', { name: 'Add to cart' }).click();
  await page.getByRole('heading', { name: 'Welcome back' }).waitFor();
  await caption(page, 'Sign in required to shop', 'Guests are sent to sign in, then returned to where they were');
  await pause(page, 2000);
  await page.getByRole('link', { name: 'Create an account' }).click();
  await page.getByRole('heading', { name: 'Create your account' }).waitFor();
  await caption(page, 'Create a customer account');
  await type(page.getByLabel('Full name'), CUSTOMER.name);
  await type(page.getByLabel('Email'), CUSTOMER.email);
  await type(page.getByLabel('Password'), CUSTOMER.password);
  await pause(page, 600);
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('button', { name: 'Sign out' }).waitFor();

  // 4. Product page, quantity 2
  await page.goto(BASE + '/?category=apparel');
  await page.locator('a.name', { hasText: 'Heather Grey Fleece Hoodie' }).click();
  await page.getByRole('heading', { name: 'Heather Grey Fleece Hoodie' }).waitFor();
  await caption(page, 'Product details', 'Price, availability and quantity — signed in as the new customer');
  await pause(page, 1800);
  await page.locator('label.qty select').selectOption('2');
  await pause(page, 600);
  await page.getByRole('button', { name: 'Add to cart' }).click();
  await page.getByText('Added to cart').waitFor();
  await pause(page, 1500);

  // 5. Add more clothes from the grid
  await page.goto(BASE + '/?category=apparel');
  await page.getByText('Essential Navy Tee').waitFor();
  await caption(page, 'Add a tee and a cap');
  for (const name of ['Essential Navy Tee', 'Washed Cotton Cap']) {
    const card = page.locator('article', { hasText: name });
    await card.scrollIntoViewIfNeeded();
    await card.getByRole('button', { name: 'Add to cart' }).click();
    await card.getByText('Added to cart').waitFor();
    await pause(page, 900);
  }

  // 6. Cart
  await page.getByRole('link', { name: 'Cart' }).click();
  await page.getByRole('heading', { name: 'Your cart' }).waitFor();
  await caption(page, 'Cart', 'Totals are always recalculated by the server');
  await pause(page, 1800);
  await page.locator('.cart-line', { hasText: 'Essential Navy Tee' }).getByRole('button', { name: 'Increase quantity' }).click();
  await pause(page, 1600);

  // 7. Checkout
  await page.getByRole('link', { name: 'Proceed to checkout' }).click();
  await page.getByText('1. Shipping address').waitFor();
  await caption(page, 'Secure checkout', 'Add a shipping address, review items, then pay');
  await type(page.getByLabel('Full name'), CUSTOMER.name);
  await type(page.getByLabel('Address line 1'), '42 Demo Avenue');
  await type(page.getByLabel('City'), 'Austin');
  await type(page.getByLabel('State / region'), 'TX');
  await type(page.getByLabel('Postal code'), '73301');
  await page.getByRole('button', { name: 'Save and use this address' }).click();
  await page.getByText('42 Demo Avenue').waitFor();
  await pause(page, 1600);
  await caption(page, 'Pay', 'Stock is reserved in one database transaction, then you are sent to the provider');
  await pause(page, 1600);
  await page.getByRole('button', { name: /^Pay / }).click();

  // 8. Hosted payment page (sandbox provider)
  await page.getByText('SANDBOX').waitFor();
  await caption(page, 'Provider-hosted payment page', 'Sandbox provider — card details never touch the store');
  await pause(page, 2600);
  await page.getByRole('button', { name: 'Pay now (success)' }).click();

  // 9. Confirmation (status comes from the signed webhook)
  await page.getByRole('heading', { name: 'Payment confirmed' }).waitFor({ timeout: 30000 });
  await caption(page, 'Payment confirmed', 'Signed webhook verified → order PAID, stock committed');
  await pause(page, 2600);
  await page.getByRole('link', { name: 'View order' }).click();
  await page.getByText('Payment confirmed').first().waitFor();
  await caption(page, 'Order tracking', 'Customers follow payment and fulfillment progress');
  await pause(page, 2800);

  // 10. Admin
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.goto(BASE + '/account/login');
  await caption(page, 'Store admin signs in');
  await type(page.getByLabel('Email'), ADMIN.email);
  await type(page.getByLabel('Password'), ADMIN.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('heading', { name: 'Overview' }).waitFor();
  await caption(page, 'Admin dashboard', 'One paid order is ready to ship');
  await pause(page, 2600);

  await page.getByRole('link', { name: 'Products & stock' }).click();
  await page.getByText('Heather Grey Fleece Hoodie').waitFor();
  await caption(page, 'Products & stock', 'Create, edit or archive products and adjust inventory');
  await pause(page, 2600);

  await page.getByRole('link', { name: 'Orders', exact: true }).click();
  await page.getByRole('link', { name: 'Open' }).first().click();
  await page.getByRole('button', { name: 'Mark as shipped' }).waitFor();
  await caption(page, 'Fulfillment', 'Only paid orders can be shipped');
  await pause(page, 2000);
  await page.getByRole('button', { name: 'Mark as shipped' }).click();
  await page.getByRole('button', { name: 'Mark as delivered' }).waitFor();
  await pause(page, 1500);
  await page.getByRole('button', { name: 'Mark as delivered' }).click();
  await page.locator('.badge', { hasText: 'Delivered' }).first().waitFor();
  await caption(page, 'Delivered ✓', 'Placed → paid → shipped → delivered');
  await pause(page, 3000);

  await page.goto(BASE + '/?category=apparel');
  await page.getByText('Heather Grey Fleece Hoodie').waitFor();
  await caption(page, 'Just Fast', 'Spring Boot · Angular · MySQL · Docker · Kubernetes');
  await pause(page, 2500);

  const video = page.video();
  await context.close();
  await browser.close();
  const src = await video.path();
  const dest = path.join(OUT_DIR, 'just-fast-demo.webm');
  fs.renameSync(src, dest);
  console.log('VIDEO', dest);
})().catch((e) => {
  console.error('DEMO FAILED', e);
  process.exit(1);
});
