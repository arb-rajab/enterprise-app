-- Demo/reference data so the app is usable immediately after a fresh `docker compose up`.
-- All seeded accounts share the password "Password123!" (bcrypt hash below) - this is a
-- throwaway demo credential, never used for anything beyond local exploration of this
-- portfolio project. See docs/project-memory/06-security.md for the full caveat.

INSERT INTO departments (code, name, cost_center) VALUES
    ('ENG', 'Engineering', 'CC-100'),
    ('SALES', 'Sales', 'CC-200'),
    ('FIN', 'Finance', 'CC-300'),
    ('OPS', 'Operations', 'CC-400');

INSERT INTO users (email, password_hash, first_name, last_name, department_id) VALUES
    ('admin@procureflow.test', '$2b$10$3L41Lyp/01u7iTauvbeVKOdGDmLPT2OgqX3HPVlUTfB6Ai.0kOwZK', 'Ada', 'Admin',
        (SELECT id FROM departments WHERE code = 'OPS')),
    ('manager@procureflow.test', '$2b$10$3L41Lyp/01u7iTauvbeVKOdGDmLPT2OgqX3HPVlUTfB6Ai.0kOwZK', 'Morgan', 'Manager',
        (SELECT id FROM departments WHERE code = 'ENG')),
    ('procurement@procureflow.test', '$2b$10$3L41Lyp/01u7iTauvbeVKOdGDmLPT2OgqX3HPVlUTfB6Ai.0kOwZK', 'Priya', 'Procurement',
        (SELECT id FROM departments WHERE code = 'OPS')),
    ('finance@procureflow.test', '$2b$10$3L41Lyp/01u7iTauvbeVKOdGDmLPT2OgqX3HPVlUTfB6Ai.0kOwZK', 'Frank', 'Finance',
        (SELECT id FROM departments WHERE code = 'FIN')),
    ('employee@procureflow.test', '$2b$10$3L41Lyp/01u7iTauvbeVKOdGDmLPT2OgqX3HPVlUTfB6Ai.0kOwZK', 'Eli', 'Employee',
        (SELECT id FROM departments WHERE code = 'ENG'));

INSERT INTO user_roles (user_id, role)
    SELECT id, 'ROLE_ADMIN' FROM users WHERE email = 'admin@procureflow.test'
    UNION ALL
    SELECT id, 'ROLE_DEPARTMENT_MANAGER' FROM users WHERE email = 'manager@procureflow.test'
    UNION ALL
    SELECT id, 'ROLE_PROCUREMENT_OFFICER' FROM users WHERE email = 'procurement@procureflow.test'
    UNION ALL
    SELECT id, 'ROLE_FINANCE_APPROVER' FROM users WHERE email = 'finance@procureflow.test'
    UNION ALL
    SELECT id, 'ROLE_EMPLOYEE' FROM users WHERE email = 'employee@procureflow.test';

UPDATE departments SET manager_user_id = (SELECT id FROM users WHERE email = 'manager@procureflow.test')
    WHERE code = 'ENG';

INSERT INTO vendors (name, contact_email, phone, address, tax_id, status) VALUES
    ('Northwind Office Supplies', 'sales@northwind-office.test', '+1-555-0100', '100 Market St, Springfield', 'TAX-1001', 'ACTIVE'),
    ('Contoso Hardware Ltd', 'orders@contoso-hardware.test', '+1-555-0101', '200 Industrial Ave, Springfield', 'TAX-1002', 'ACTIVE'),
    ('Fabrikam Cloud Services', 'billing@fabrikam-cloud.test', '+1-555-0102', '300 Tech Park Dr, Springfield', 'TAX-1003', 'PENDING_APPROVAL');

INSERT INTO catalog_items (sku, name, description, category, unit_price, vendor_id) VALUES
    ('OFF-CHAIR-01', 'Ergonomic Office Chair', 'Adjustable-height mesh-back office chair', 'Furniture', 249.99,
        (SELECT id FROM vendors WHERE name = 'Northwind Office Supplies')),
    ('OFF-DESK-01', 'Standing Desk', 'Electric sit/stand desk, 60in', 'Furniture', 549.00,
        (SELECT id FROM vendors WHERE name = 'Northwind Office Supplies')),
    ('HW-LAPTOP-14', '14-inch Developer Laptop', '32GB RAM / 1TB SSD business laptop', 'Hardware', 1899.00,
        (SELECT id FROM vendors WHERE name = 'Contoso Hardware Ltd')),
    ('HW-MONITOR-27', '27-inch 4K Monitor', 'USB-C 4K IPS monitor', 'Hardware', 429.50,
        (SELECT id FROM vendors WHERE name = 'Contoso Hardware Ltd')),
    ('SW-CLOUD-SEAT', 'Cloud IDE Seat License (annual)', 'Per-seat annual subscription', 'Software', 199.00,
        (SELECT id FROM vendors WHERE name = 'Fabrikam Cloud Services'));
