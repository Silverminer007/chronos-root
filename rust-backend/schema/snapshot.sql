-- Canonical description of the shared schema, one line per table, column,
-- constraint, index and sequence, sorted. Used to compare the Rust schema with the
-- schema the Java Flyway migrations produce (see kb/adr/ADR-2026-10-12-014-rust-schema-identical-to-java.md).
--
-- Left out on purpose:
--   * Rust-only tables (events, _sqlx_migrations)
--   * the *_backup tables Flyway V1.1.0 leaves behind (CREATE TABLE AS SELECT, no entity)
WITH shared AS (
    SELECT c.oid, c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND c.relname NOT IN ('events', '_sqlx_migrations')
      AND c.relname NOT LIKE '%\_backup'
)
SELECT line
FROM (
    SELECT 'table ' || relname AS line
    FROM shared
    UNION ALL
    SELECT 'column ' || s.relname || '.' || a.attname
           || ' #' || row_number() OVER (PARTITION BY s.relname ORDER BY a.attnum)
           || ' ' || format_type(a.atttypid, a.atttypmod)
           || CASE WHEN a.attnotnull THEN ' NOT NULL' ELSE '' END
           || COALESCE(' DEFAULT ' || pg_get_expr(d.adbin, d.adrelid), '')
    FROM shared s
    JOIN pg_attribute a ON a.attrelid = s.oid AND a.attnum > 0 AND NOT a.attisdropped
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    UNION ALL
    SELECT 'constraint ' || s.relname || '.' || con.conname || ' ' || pg_get_constraintdef(con.oid)
    FROM shared s
    JOIN pg_constraint con ON con.conrelid = s.oid
    UNION ALL
    SELECT 'index ' || i.indexname || ' ' || i.indexdef
    FROM pg_indexes i
    WHERE i.schemaname = 'public'
      AND i.tablename IN (SELECT relname FROM shared)
    UNION ALL
    SELECT 'sequence ' || sequencename || ' ' || data_type::text
           || ' start=' || start_value || ' increment=' || increment_by
           || ' min=' || min_value || ' max=' || max_value || ' cache=' || cache_size
    FROM pg_sequences
    WHERE schemaname = 'public'
) t
ORDER BY line COLLATE "C";
