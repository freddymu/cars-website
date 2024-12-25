package com.pstag.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class SqlQueryBuilder {

    // Define allowed tables and columns to prevent injection via identifiers
    private static final Set<String> ALLOWED_TABLES = Set.of("cars");

    private String table;
    private List<String> columns = new ArrayList<>();
    private List<String> whereClauses = new ArrayList<>();
    private List<Object> parameters = new ArrayList<>();
    private List<String> orderBy = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private Integer paramIndex = 1;

    /**
     * Select specific columns.
     *
     * @param columns Columns to select.
     * @return The builder instance.
     */
    public SqlQueryBuilder select(String... columns) {
        for (String column : columns) {
            validateColumn(column);
            this.columns.add(column);
        }
        return this;
    }

    /**
     * Specify the table to select from.
     *
     * @param table Table name.
     * @return The builder instance.
     */
    public SqlQueryBuilder from(String table) {
        validateTable(table);
        this.table = table;
        return this;
    }

    /**
     * Add a WHERE clause with parameters.
     *
     * @param clause SQL condition with placeholders (?).
     * @param params Parameters corresponding to the placeholders.
     * @return The builder instance.
     */
    public SqlQueryBuilder where(String clause, Object... params) {
        if (!clause.contains("$") && !clause.contains("IS NULL")) {
            throw new IllegalArgumentException(
                    "WHERE clause must contain at least one parameter placeholder '$': " + clause);
        }

        if (!clause.contains("IS NULL")) {

            if (params.length == 1) {
                clause = clause.replace("$", "$" + paramIndex++);
            } else {
                for (Object param : params) {
                    clause = clause.replaceFirst(" \\$ ", " \\$" + paramIndex++ + " ");
                }
            }
        }
        this.whereClauses.add(clause);
        Collections.addAll(this.parameters, params);
        return this;
    }

    /**
     * Add ORDER BY clauses.
     *
     * @param columns Columns to order by.
     * @return The builder instance.
     */
    public SqlQueryBuilder orderBy(String... columns) {
        for (String column : columns) {
            validateColumn(column);
            this.orderBy.add(column);
        }
        return this;
    }

    /**
     * Add a LIMIT clause.
     *
     * @param limit Maximum number of records to retrieve.
     * @return The builder instance.
     */
    public SqlQueryBuilder limit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative: " + limit);
        }
        this.limit = limit;
        return this;
    }

    /**
     * Add an OFFSET clause.
     *
     * @param offset Number of records to skip.
     * @return The builder instance.
     */
    public SqlQueryBuilder offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        this.offset = offset;
        return this;
    }

    /**
     * Build the final Query object containing the SQL string with placeholders and
     * the parameters.
     *
     * @return The built Query.
     */
    public Query build() {
        if (table == null || columns.isEmpty()) {
            throw new IllegalStateException("Table name and at least one column must be specified");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");

        // Add columns
        StringJoiner columnJoiner = new StringJoiner(", ");
        for (String column : columns) {
            columnJoiner.add(column);
        }
        sql.append(columnJoiner.toString());

        // Add table
        sql.append(" FROM ").append(table);

        // Add WHERE clauses
        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ");
            StringJoiner whereJoiner = new StringJoiner(" AND ");
            for (String clause : whereClauses) {
                whereJoiner.add(clause);
            }
            sql.append(whereJoiner.toString());
        }

        // Add ORDER BY
        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (String column : orderBy) {
                orderJoiner.add(column);
            }
            sql.append(orderJoiner.toString());
        }

        // Add LIMIT
        if (limit != null) {
            sql.append(" LIMIT $" + paramIndex++);
            this.parameters.add(limit);
        }

        // Add OFFSET
        if (offset != null) {
            sql.append(" OFFSET $" + paramIndex++);
            this.parameters.add(offset);
        }

        return new Query(sql.toString(), List.copyOf(parameters));
    }

    /**
     * Builds a SQL query to count the number of rows in the specified table that
     * match the given conditions.
     * 
     * @return a {@link Query} object containing the SQL count query and its
     *         parameters.
     * @throws IllegalStateException if the table name is not specified or no
     *                               columns are provided.
     */
    public Query count() {
        if (table == null || columns.isEmpty()) {
            throw new IllegalStateException("Table name and at least one column must be specified");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(id) as total_rows");

        // Add table
        sql.append(" FROM ").append(table);

        // Add WHERE clauses
        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ");
            StringJoiner whereJoiner = new StringJoiner(" AND ");
            for (String clause : whereClauses) {
                whereJoiner.add(clause);
            }
            sql.append(whereJoiner.toString());
        }

        return new Query(sql.toString(), List.copyOf(parameters));
    }

    /**
     * Validate that the column name is allowed.
     *
     * @param column Column name to validate.
     */
    private void validateColumn(String column) {
        // if (!ALLOWED_COLUMNS.contains(column.toLowerCase())) {
        // throw new IllegalArgumentException("Invalid column name: " + column);
        // }
    }

    /**
     * Validate that the table name is allowed.
     *
     * @param table Table name to validate.
     */
    private void validateTable(String table) {
        if (!ALLOWED_TABLES.contains(table.toLowerCase())) {
            throw new IllegalArgumentException("Invalid table name: " + table);
        }
    }

    /**
     * Immutable Query class holding the SQL string and parameters.
     */
    public static class Query {
        private final String sql;
        private final List<Object> parameters;

        public Query(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        }

        public String getSql() {
            return sql;
        }

        public List<Object> getParameters() {
            return parameters;
        }
    }
}