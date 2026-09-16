-- Core schema for ProcureFlow.
-- managers/requesters are kept as plain FKs where a genuine dependency exists;
-- departments.manager_user_id intentionally has NO foreign key, because departments and users
-- reference each other and Flyway migrations run in a single ordered pass (see ADR-0001).

CREATE TABLE departments (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(120) NOT NULL,
    cost_center     VARCHAR(40),
    manager_user_id BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(190) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(80)  NOT NULL,
    last_name       VARCHAR(80)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    department_id   BIGINT REFERENCES departments (id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE vendors (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(160) NOT NULL,
    contact_email  VARCHAR(190) NOT NULL,
    phone          VARCHAR(30),
    address        VARCHAR(250),
    tax_id         VARCHAR(40),
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE catalog_items (
    id          BIGSERIAL PRIMARY KEY,
    sku         VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    category    VARCHAR(60),
    unit_price  NUMERIC(12, 2) NOT NULL,
    vendor_id   BIGINT NOT NULL REFERENCES vendors (id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE purchase_requisitions (
    id            BIGSERIAL PRIMARY KEY,
    requester_id  BIGINT NOT NULL REFERENCES users (id),
    department_id BIGINT NOT NULL REFERENCES departments (id),
    justification VARCHAR(1000),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE requisition_line_items (
    id              BIGSERIAL PRIMARY KEY,
    requisition_id  BIGINT NOT NULL REFERENCES purchase_requisitions (id) ON DELETE CASCADE,
    catalog_item_id BIGINT REFERENCES catalog_items (id),
    description     VARCHAR(250) NOT NULL,
    quantity        INTEGER NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    line_total      NUMERIC(14, 2) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE approval_steps (
    id                  BIGSERIAL PRIMARY KEY,
    requisition_id      BIGINT NOT NULL REFERENCES purchase_requisitions (id) ON DELETE CASCADE,
    step_order          INTEGER NOT NULL,
    approver_role       VARCHAR(40) NOT NULL,
    decided_by_user_id  BIGINT REFERENCES users (id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comments            VARCHAR(500),
    decided_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_approval_steps_requisition_order UNIQUE (requisition_id, step_order)
);

CREATE TABLE purchase_orders (
    id             BIGSERIAL PRIMARY KEY,
    requisition_id BIGINT NOT NULL UNIQUE REFERENCES purchase_requisitions (id),
    vendor_id      BIGINT NOT NULL REFERENCES vendors (id),
    po_number      VARCHAR(30) NOT NULL UNIQUE,
    total_amount   NUMERIC(14, 2) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id                BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders (id),
    invoice_number    VARCHAR(60) NOT NULL UNIQUE,
    amount            NUMERIC(14, 2) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE audit_log_entries (
    id           BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(60) NOT NULL,
    entity_id    BIGINT NOT NULL,
    action       VARCHAR(60) NOT NULL,
    performed_by VARCHAR(190),
    details      VARCHAR(1000),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_requisitions_requester ON purchase_requisitions (requester_id);
CREATE INDEX idx_requisitions_status ON purchase_requisitions (status);
CREATE INDEX idx_approval_steps_status ON approval_steps (status);
CREATE INDEX idx_audit_entity ON audit_log_entries (entity_type, entity_id);
