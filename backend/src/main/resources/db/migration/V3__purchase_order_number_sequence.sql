-- PurchaseOrderService previously derived po_number from purchase_orders.count() + 1,
-- which is a non-atomic read-then-format: two concurrent conversions can read the same
-- count and race to insert the same po_number, which the UNIQUE constraint on
-- po_number rejects with a DataIntegrityViolationException for the loser instead of
-- letting the request succeed. A database sequence hands out a distinct value per
-- caller atomically, with no locking and no read-then-write window.
CREATE SEQUENCE purchase_order_number_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;
