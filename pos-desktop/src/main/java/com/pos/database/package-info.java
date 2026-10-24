/**
 * Shop MySQL connections, schema creation and repeatable startup upgrades.
 *
 * <p>{@link com.pos.database.DatabaseSetup#run()} creates the base schema, repairs
 * legacy sale sequencing and applies the ledger, promotion and companion changes.
 * The effective schema is the combination of these classes, not just the reference
 * SQL file. MySQL DDL may commit implicitly: startup upgrades are not one atomic
 * transaction and must not be mixed into a checkout connection.
 *
 * <p>Business transaction boundaries belong to the services package. Connection
 * credentials are edition-specific; runtime accounts retain schema-local DDL
 * privileges for subsequent upgrades. See {@code docs/database.md} for the schema
 * lifecycle, evidence-retention rules and links to the table dictionary.
 */
package com.pos.database;
