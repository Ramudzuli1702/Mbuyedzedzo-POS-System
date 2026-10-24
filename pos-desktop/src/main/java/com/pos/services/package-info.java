/**
 * Shop workflows and JDBC transaction boundaries behind desktop and phone views.
 *
 * <p>Services validate staff roles, ownership and current database state rather
 * than trusting a visible button or client-supplied identifier. Multi-row writes
 * such as checkout and approval use one connection and roll back together. Helpers
 * accepting an existing connection participate in the caller's transaction;
 * opening another connection inside them would break locking/atomicity.
 *
 * <p>Use {@link java.math.BigDecimal} for money. Recorded net sale prices and
 * historical costs are evidence; current product prices/costs must not replace
 * them in refunds or historical reporting. Connection-injectable stocktake and
 * promotion services support isolated integration fixtures. See
 * {@code docs/code-architecture.md} for call flows and extension points.
 */
package com.pos.services;
