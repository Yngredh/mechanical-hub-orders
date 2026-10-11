-- Tabelas de dominio do mechanical-hub-orders (data_model.md §2).
-- Migrations novas: nao herdam o historico Flyway do monolito.

CREATE TABLE profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Colunas lidas pela Lambda `authenticate` (mechanical-hub-auth):
-- id, name, document_number, password_hash, deleted_at, profile_id.
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID         NOT NULL REFERENCES profiles (id) ON DELETE RESTRICT,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    document_number VARCHAR(11)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

COMMENT ON COLUMN users.document_number IS
    'Documento do funcionario (CPF), somente digitos. Login da funcao de autenticacao.';

CREATE INDEX idx_users_profile_id ON users (profile_id);

CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    document_type   VARCHAR(10)  NOT NULL,
    document_number VARCHAR(20)  NOT NULL UNIQUE,
    telephone       VARCHAR(20)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE vehicles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    license_plate VARCHAR(10)  NOT NULL UNIQUE,
    brand         VARCHAR(100) NOT NULL,
    model         VARCHAR(100) NOT NULL,
    year          INTEGER      NOT NULL,
    color         VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

CREATE INDEX idx_vehicles_customer_id ON vehicles (customer_id);

-- Catalogo de reparos sem preco (o preco e do billing). id = repairId.
CREATE TABLE services (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

-- Numero sequencial da ordem (OS-yyyyMM-NNNN), sem corrida entre instancias.
CREATE SEQUENCE service_order_number_seq;

CREATE TABLE service_orders (
    id                      UUID PRIMARY KEY,
    order_number            VARCHAR(50)   NOT NULL UNIQUE,
    customer_id             UUID          NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    vehicle_id              UUID          NOT NULL REFERENCES vehicles (id) ON DELETE RESTRICT,
    created_by_user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    responsible_user_id     UUID REFERENCES users (id) ON DELETE RESTRICT,
    order_status            VARCHAR(50)   NOT NULL,
    request_description     VARCHAR(255)  NOT NULL,
    budget                  DECIMAL(19, 2),
    has_stock_pending       BOOLEAN       NOT NULL DEFAULT false,
    saga_transaction_id     UUID,
    approval_token_hash     VARCHAR(64),
    version                 BIGINT        NOT NULL DEFAULT 0,
    estimated_completion_at TIMESTAMPTZ,
    opened_at               TIMESTAMPTZ   NOT NULL,
    completed_at            TIMESTAMPTZ,
    delivered_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON COLUMN service_orders.saga_transaction_id IS
    'ref:orchestrator - transactionId recebido no sendApproval ou markStockPending';
COMMENT ON COLUMN service_orders.approval_token_hash IS
    'SHA-256 (hex) do token de aprovacao do cliente; o token em texto puro nao e persistido';

CREATE INDEX idx_service_orders_customer_id ON service_orders (customer_id);
CREATE INDEX idx_service_orders_order_status ON service_orders (order_status);
CREATE INDEX idx_service_orders_responsible_user_id ON service_orders (responsible_user_id);

CREATE TABLE order_tasks (
    id               UUID PRIMARY KEY,
    service_order_id UUID        NOT NULL REFERENCES service_orders (id) ON DELETE RESTRICT,
    service_id       UUID        NOT NULL REFERENCES services (id) ON DELETE RESTRICT,
    service_status   VARCHAR(50) NOT NULL,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    CONSTRAINT uk_order_tasks_order_service UNIQUE (service_order_id, service_id)
);

CREATE INDEX idx_order_tasks_service_order_id ON order_tasks (service_order_id);
