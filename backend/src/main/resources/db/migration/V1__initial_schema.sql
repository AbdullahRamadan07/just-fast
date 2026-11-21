-- Identity ---------------------------------------------------------------

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE addresses (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    full_name   VARCHAR(120) NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    city        VARCHAR(100) NOT NULL,
    region      VARCHAR(100),
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(2)      NOT NULL,
    phone       VARCHAR(30),
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_addresses_user (user_id)
) ENGINE = InnoDB;

-- Catalog ----------------------------------------------------------------

CREATE TABLE categories (
    id   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80)  NOT NULL,
    slug VARCHAR(80)  NOT NULL,
    CONSTRAINT uk_categories_slug UNIQUE (slug)
) ENGINE = InnoDB;

CREATE TABLE products (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(160)  NOT NULL,
    description TEXT,
    price       DECIMAL(12,2) NOT NULL,
    image_url   VARCHAR(500),
    category_id BIGINT        NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_price CHECK (price >= 0),
    INDEX ix_products_status_category (status, category_id),
    INDEX ix_products_name (name)
) ENGINE = InnoDB;

-- Inventory --------------------------------------------------------------

CREATE TABLE inventory_items (
    product_id BIGINT NOT NULL PRIMARY KEY,
    on_hand    INT    NOT NULL,
    reserved   INT    NOT NULL DEFAULT 0,
    version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_available CHECK (on_hand >= reserved)
) ENGINE = InnoDB;

-- Shopping ---------------------------------------------------------------

CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE cart_items (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    CONSTRAINT uk_cart_items_product UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB;

-- Orders -----------------------------------------------------------------

CREATE TABLE orders (
    id                     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number           VARCHAR(32)   NOT NULL,
    user_id                BIGINT        NOT NULL,
    status                 VARCHAR(30)   NOT NULL,
    currency               VARCHAR(3)       NOT NULL,
    subtotal               DECIMAL(12,2) NOT NULL,
    shipping_fee           DECIMAL(12,2) NOT NULL,
    total                  DECIMAL(12,2) NOT NULL,
    ship_full_name         VARCHAR(120)  NOT NULL,
    ship_line1             VARCHAR(200)  NOT NULL,
    ship_line2             VARCHAR(200),
    ship_city              VARCHAR(100)  NOT NULL,
    ship_region            VARCHAR(100),
    ship_postal_code       VARCHAR(20)   NOT NULL,
    ship_country           VARCHAR(2)       NOT NULL,
    ship_phone             VARCHAR(30),
    idempotency_key        VARCHAR(64)   NOT NULL,
    reservation_expires_at DATETIME(6)   NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    paid_at                DATETIME(6),
    shipped_at             DATETIME(6),
    delivered_at           DATETIME(6),
    cancelled_at           DATETIME(6),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX ix_orders_status_expiry (status, reservation_expires_at),
    INDEX ix_orders_user_created (user_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    sku          VARCHAR(64)   NOT NULL,
    product_name VARCHAR(160)  NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    currency     VARCHAR(3)       NOT NULL,
    quantity     INT           NOT NULL,
    line_total   DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE stock_reservations (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_reservations_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_reservations_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reservations_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_reservations_quantity CHECK (quantity > 0)
) ENGINE = InnoDB;

-- Payments ---------------------------------------------------------------

CREATE TABLE payment_attempts (
    id                  VARCHAR(36)      NOT NULL PRIMARY KEY,
    order_id            BIGINT        NOT NULL,
    provider            VARCHAR(30)   NOT NULL,
    provider_session_id VARCHAR(255),
    checkout_url        VARCHAR(2048),
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3)       NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    failure_reason      VARCHAR(255),
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_attempts_session UNIQUE (provider, provider_session_id),
    CONSTRAINT fk_payment_attempts_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX ix_payment_attempts_order (order_id)
) ENGINE = InnoDB;

CREATE TABLE processed_webhook_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider     VARCHAR(30)  NOT NULL,
    event_id     VARCHAR(255) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_webhook_events_provider_event UNIQUE (provider, event_id)
) ENGINE = InnoDB;

-- Spring Session JDBC (shared session store for horizontally scaled API) --

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            VARCHAR(36)     NOT NULL,
    SESSION_ID            VARCHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID VARCHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;
