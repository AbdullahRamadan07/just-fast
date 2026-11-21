<p align="center">
  <img src="frontend/public/brand/just-fast-logo.png" alt="Just Fast" width="360">
</p>

# Just Fast

Single-store e-commerce application: customers browse a catalog, manage a cart, pay through a
provider-hosted checkout, and track their orders; administrators manage the catalog, stock and fulfillment.

| Area        | Technology                                                                   |
| ----------- | ---------------------------------------------------------------------------- |
| Backend     | Java 21, Spring Boot 3.5, Spring MVC, Maven                                  |
| Security    | Spring Security (session cookie + CSRF), `CUSTOMER` / `ADMIN` roles          |
| Persistence | MySQL 8, Spring Data JPA / Hibernate, Flyway, Spring Session JDBC            |
| Frontend    | Angular 20 (standalone components, signals), TypeScript                      |
| Payments    | `PaymentGateway` interface — built-in **sandbox** adapter and **Stripe Checkout** adapter |
| Containers  | Docker, Docker Compose                                                       |
| Deployment  | Kubernetes (kustomize base + dev/prod overlays, migration Job, HTTPS ingress) |

---

## Quick start (Docker Compose)

```bash
cp .env.example .env
docker compose up --build
```

Open **http://localhost:8080**.

* Admin: `admin@justfast.local` / `change-me-admin` (from `.env`, created on startup) → **Admin** in the header.
* Customer: use **Sign in → Create an account**.
* Pay with the **sandbox provider**: after "Pay", you are redirected to a simulated hosted checkout page
  (`/api/sandbox-provider/...`). Choose *Pay now* or *Decline card*; it sends a signed webhook to the API
  exactly like a real provider and redirects you back to the order status page.

The MySQL data lives in the `mysql-data` volume (`docker compose down -v` resets it). Flyway applies
`backend/src/main/resources/db/migration` on startup and seeds a starter catalog
(one product has a single unit in stock and one is sold out, to try the stock rules).

### Running without containers

```bash
# API (needs MySQL on localhost:3306 with database/user "justfast")
cd backend && COOKIE_SECURE=false PUBLIC_BASE_URL=http://localhost:4200 ADMIN_EMAIL=admin@justfast.local ADMIN_PASSWORD=change-me-admin mvn spring-boot:run

# Web (proxies /api → localhost:8080, see proxy.conf.json)
cd frontend && npm install && npm start      # http://localhost:4200
```

---

## Architecture

### Backend modules (`backend/src/main/java/com/justfast`)

Each module separates **web** (REST controllers + DTOs), **application** (use cases),
**domain** (entities with business rules) and **infrastructure** (Spring Data repositories).

| Module      | Responsibility                                                                                     |
| ----------- | -------------------------------------------------------------------------------------------------- |
| `identity`  | Users, roles, registration/sign-in, shipping addresses, Spring Security config                     |
| `catalog`   | Categories, products (create / update / archive), storefront search with pagination                |
| `inventory` | Stock levels, atomic reservations, commit / release                                               |
| `cart`      | One cart per customer, quantity rules                                                              |
| `orders`    | Order lifecycle, purchase-time snapshots, customer & admin order queries, fulfillment              |
| `payments`  | `PaymentGateway` + adapters, payment attempts, signed webhooks, idempotent event processing, reconciliation |
| `checkout`  | Orchestrates cart → order placement → stock reservation → hosted payment session                  |
| `common`    | Configuration, money arithmetic, pagination, centralized API error handling                        |

### Frontend (`frontend/src/app`)

Feature-based lazy routes: `features/storefront`, `features/checkout` (cart, checkout, payment return),
`features/account` (sign-in, registration, orders, addresses) and `features/admin` (overview, orders,
products & stock, categories). Shared building blocks live in `core/` (API clients, auth, cart store,
guards) and `shared/` (logo, status badges, address form, timeline).

**Branding** — `shared/logo.ts` renders the horizontal lockup on the light storefront header and at
checkout, and the shopping-bag mark alone in tight spaces (mobile header, sign-in card, footer, favicon).
Brand colors sampled from the logo are defined as CSS variables in `src/styles.scss`
(`--jf-blue: #006ffa`, `--jf-navy: #03173f`). Assets are in `frontend/public/brand/`.

### Data model

| Area      | Tables                                         |
| --------- | ---------------------------------------------- |
| Identity  | `users`, `addresses`                           |
| Catalog   | `categories`, `products`                       |
| Inventory | `inventory_items`, `stock_reservations`        |
| Shopping  | `carts`, `cart_items`                          |
| Orders    | `orders`, `order_items`                        |
| Payments  | `payment_attempts`, `processed_webhook_events` |
| Sessions  | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`  |

Money is `BigDecimal` in Java and `DECIMAL(12,2)` in MySQL. Orders store purchase-time product names,
SKUs, unit prices, currency and the shipping address. Unique constraints: product SKU,
`(user_id, idempotency_key)` on orders, `(provider, event_id)` on webhook events,
`(provider, provider_session_id)` on payment attempts. CHECK constraints keep `0 ≤ reserved ≤ on_hand`.

### Checkout and payment flow

1. Angular posts `POST /api/checkout {addressId, idempotencyKey}`. The key is generated once per checkout
   page, so retries and double-clicks return the same order.
2. In **one MySQL transaction** (`OrderPlacementService`): prices are recomputed from the catalog, the
   address ownership and shipping region are checked, stock is reserved with a conditional
   `UPDATE … WHERE on_hand - reserved >= qty` (row-locked; two buyers cannot both take the last unit),
   a `PENDING_PAYMENT` order and a `CREATED` payment attempt are inserted, and the cart is emptied.
3. **Outside** the transaction (`PaymentSessionService`), the hosted checkout session is created using the
   payment-attempt id as the provider idempotency key; the customer is redirected to the provider.
4. The provider calls `POST /api/payments/webhooks/{provider}`. The signature is verified
   (HMAC-SHA256, timestamp tolerance), the event id is recorded (unique per provider) and the outcome is
   applied — payment attempt, order status and stock — **in one transaction under an order row lock**,
   after matching amount and currency. The return page polls `GET /api/orders/{id}` for the real status.
5. `PaymentReconciliationJob` (every minute) handles orders whose reservation expired: it asks the
   provider for the session state, expires still-open sessions, applies late outcomes, and releases stock
   only when no attempt can still succeed. A payment that succeeds after its order was cancelled (or with a
   mismatched amount) moves the order to `PAYMENT_REVIEW`, which can never be shipped.

Payment-attempt status (`CREATED → PENDING → SUCCEEDED | FAILED | EXPIRED`) is tracked separately from the
order/fulfillment status (`PENDING_PAYMENT → PAID → SHIPPED → DELIVERED`, or `CANCELLED` / `PAYMENT_REVIEW`).

### Security

* Session cookie `JF_SESSION` (HttpOnly, `Secure` behind HTTPS, SameSite=Lax), stored in MySQL via
  Spring Session JDBC so the API scales horizontally. Session id is rotated on sign-in.
* CSRF: `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header for every browser write (Angular does this
  automatically). Webhooks are exempt and authenticated by provider signature instead.
* `/api/admin/**` requires `ROLE_ADMIN`. Carts, addresses and orders are always loaded by
  `(id, current user)`, so another customer's resources answer `404`.
* The SPA and API are served from **one HTTPS origin** (ingress routes `/api` → API, `/` → web).
* Card data is only ever entered on the provider's page; provider keys stay on the server.

---

## Payments: choosing a provider

The MVP uses one currency, one shipping region and one provider. Checkout depends only on
`payments/gateway/PaymentGateway.java`; adapters are selected with `PAYMENT_PROVIDER`:

| Value     | Use                                                                                                 |
| --------- | --------------------------------------------------------------------------------------------------- |
| `sandbox` | Default for local development. In-memory simulated hosted checkout; **single instance only**.     |
| `stripe`  | Stripe Checkout via REST (no SDK). Use test keys (`sk_test_…`) and the webhook signing secret.     |

Before going live, confirm merchant onboarding and currency support with the chosen provider. To try
Stripe's sandbox locally:

```bash
# .env
PAYMENT_PROVIDER=stripe
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...       # printed by the command below
stripe listen --forward-to localhost:8080/api/payments/webhooks/stripe
```

Handled Stripe events: `checkout.session.completed`, `checkout.session.async_payment_succeeded`,
`checkout.session.async_payment_failed`, `checkout.session.expired`.
To add another provider, implement `PaymentGateway` and register it with
`@ConditionalOnProperty(prefix = "payments", name = "provider", havingValue = "<name>")`.

---

## Configuration

| Variable                                  | Default                         | Purpose                                              |
| ----------------------------------------- | ------------------------------- | ---------------------------------------------------- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`    | local MySQL                     | Database connection                                  |
| `FLYWAY_ENABLED`                          | `true`                          | Run migrations on startup (`false` in Kubernetes)    |
| `PUBLIC_BASE_URL`                         | `https://localhost`             | Public origin, used for payment return URLs          |
| `COOKIE_SECURE`                           | `true`                          | Set `false` only for plain-HTTP local development    |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`           | —                               | Admin account created/promoted at startup            |
| `STORE_CURRENCY`                          | `USD`                           | Single store currency                                |
| `STORE_SHIPPING_COUNTRY`                  | `US`                            | Single shipping region (ISO country)                 |
| `STORE_SHIPPING_FEE`                      | `5.00`                          | Flat shipping fee                                    |
| `RESERVATION_TTL`                         | `PT30M`                         | How long unpaid orders hold stock                    |
| `PAYMENT_PROVIDER`                        | `sandbox`                       | `sandbox` or `stripe`                                |
| `SANDBOX_WEBHOOK_SECRET`, `SANDBOX_WEBHOOK_URL` | dev values                | Sandbox signing secret / delivery URL                |
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` | —                               | Stripe credentials                                   |

---

## Tests

```bash
cd backend
mvn test      # unit tests: pricing, order lifecycle, payment-outcome rules, webhook signatures, …
mvn verify    # + CheckoutFlowIT against MySQL 8.4 in Testcontainers (requires Docker)
```

`CheckoutFlowIT` covers the acceptance criteria:

| Acceptance criterion                                                         | Test                                                         |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Find a product, pay in the sandbox, see the confirmed order                  | `customerFindsProductPaysInSandboxAndSeesConfirmedOrder`     |
| Two concurrent checkouts cannot reserve the last unit                        | `twoConcurrentCheckoutsCannotReserveTheLastUnit`             |
| Repeated checkouts / duplicate events don't create orders or consume twice   | `repeatedCheckoutRequests…`, `duplicatePaymentEvents…`       |
| Failed or expired payments release stock and cannot be fulfilled             | `failedPayment…`, `expiredUnpaidOrder…`, `latePaymentOnExpiredOrder…` |
| Customers can't see others' orders or call admin endpoints                   | `customersCannotSeeOtherCustomersOrdersOrCallAdminEndpoints` |
| Webhook signatures and CSRF are enforced                                     | `webhooksWithInvalidSignaturesAreRejected`, `browserWritesRequireCsrfToken` |
| Fresh environment applies migrations                                         | every IT run starts from an empty MySQL and runs Flyway      |

---

## Kubernetes

```
deploy/k8s/
├── base/               # API + web Deployments/Services, HTTPS Ingress, ConfigMap, migration Job
├── overlays/dev/       # in-cluster MySQL StatefulSet (PVC), sandbox payments, generated Secret
├── overlays/prod/      # managed MySQL, Stripe, pinned image tags; Secret supplied externally
└── deploy.sh           # platform → migration Job (wait) → application rollout
```

```bash
docker build -t ghcr.io/your-org/just-fast-api:0.1.0 backend
docker build -t ghcr.io/your-org/just-fast-web:0.1.0 frontend
docker push ghcr.io/your-org/just-fast-api:0.1.0 && docker push ghcr.io/your-org/just-fast-web:0.1.0

cp deploy/k8s/overlays/dev/secrets.env.example deploy/k8s/overlays/dev/secrets.env   # edit values
deploy/k8s/deploy.sh deploy/k8s/overlays/dev
```

* The API Deployment runs with `FLYWAY_ENABLED=false`; the `just-fast-migrate` Job (API image,
  `migrate` profile) applies migrations and exits before the new version rolls out.
* Probes: `/actuator/health/{liveness,readiness}` (API) and `/healthz` (web). Resource requests/limits,
  non-root, read-only root filesystem for the API.
* Secrets (`just-fast-secrets`) keys: `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`,
  `SANDBOX_WEBHOOK_SECRET`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` (+ `MYSQL_ROOT_PASSWORD` in dev).
* Production: point `DB_URL` at a managed MySQL with automated backups; sessions are shared through
  Spring Session JDBC, so the API runs with several replicas. Use `PAYMENT_PROVIDER=stripe`
  (the sandbox provider is single-instance).
* Update `shop.example.com`, the ingress class and the cert-manager issuer for your cluster.

---

## Demo video

[`docs/demo/just-fast-demo.webm`](docs/demo/just-fast-demo.webm) (≈1 min) walks through browsing the clothing range,
sign-up, cart, checkout, sandbox payment, order tracking and admin fulfillment. To re-record it against a
running Compose stack:

```bash
npm i playwright@1.55.0 && npx playwright install chromium
node docs/demo/record-demo.js docs/demo
```
