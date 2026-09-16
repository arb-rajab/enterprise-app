# Project Brief

## What this is

**ProcureFlow** is a skill-demonstration enterprise application built to show a coherent,
professionally structured Spring Boot + Angular + PostgreSQL stack in one repository. It is part
of a developer portfolio and is not a real production system for a real organization — the
"procurement and approval workflow" domain was chosen because it naturally requires the things an
enterprise-style demo needs to prove out:

- role-based access control with more than one meaningfully different role
- a non-trivial relational data model (not just a single CRUD resource)
- a multi-step, stateful business workflow (not just create/read/update/delete)
- a defensible authentication and authorization approach
- a documented integration contract between a separate frontend and backend

## The scenario (contrived, but internally consistent)

A mid-size company's Procurement department needs a system for employees to request goods and
services, route those requests through the right approvers based on how much they cost, convert
approved requests into purchase orders against vendors, and reconcile incoming vendor invoices
against those orders.

## Roles

| Role                     | Represents                                    |
| ------------------------ | ---------------------------------------------- |
| `ROLE_EMPLOYEE`           | Any staff member; can raise requisitions       |
| `ROLE_DEPARTMENT_MANAGER` | Approves requisitions for their department     |
| `ROLE_PROCUREMENT_OFFICER`| Manages vendors/catalog, converts POs, second-tier approver |
| `ROLE_FINANCE_APPROVER`   | Approves the largest requisitions, handles invoice payment |
| `ROLE_ADMIN`              | Full administrative access, department/user management |

## Core workflow

1. An employee drafts a **Purchase Requisition** with one or more line items (optionally drawn
   from a shared **Catalog**).
2. On submission, the system builds an **approval chain** whose length depends on the
   requisition's total amount (see `docs/project-memory/adr/0004-tiered-approval-thresholds.md`).
3. Approvers act on their step in sequence. Any rejection stops the chain; once every step is
   approved, the requisition is `APPROVED`.
4. A Procurement Officer converts an `APPROVED` requisition into a **Purchase Order** against a
   chosen, active **Vendor**.
5. Vendor **Invoices** are recorded against purchase orders and move through
   received → approved → paid (or disputed).

Every state-changing action is written to an append-only **audit log**.

## Scope boundaries for this demo

- No payment gateway integration — "paid" is a status flag, not a real transaction.
- No email/notification delivery.
- Vendor and catalog data entry is manual (no external supplier integration).
- Single-tenant; no multi-company/org support.

See `docs/project-memory/02-requirements.md` for the full functional/non-functional requirement
list and `docs/project-memory/09-backlog.md` for what is intentionally deferred.
