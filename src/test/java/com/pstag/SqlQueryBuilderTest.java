package com.pstag;

import io.quarkus.test.junit.QuarkusTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pstag.utils.SqlQueryBuilder;
import com.pstag.utils.SqlQueryBuilder.Query;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class SqlQueryBuilderTest {
    @Test
    void testSelectFromWhere() {
        SqlQueryBuilder builder = new SqlQueryBuilder()
                .select("id", "name")
                .from("cars")
                .where("id = $", 1);

        Query query = builder.build();

        assertEquals("SELECT id, name FROM cars WHERE id = $1", query.getSql());
        assertEquals(1, query.getParameters().get(0));
    }

    @Test
    void testOrderBy() {
        SqlQueryBuilder builder = new SqlQueryBuilder()
                .select("id", "name")
                .from("cars")
                .orderBy("name");

        Query query = builder.build();

        assertEquals("SELECT id, name FROM cars ORDER BY name", query.getSql());
        assertTrue(query.getParameters().isEmpty());
    }

    @Test
    void testLimit() {
        SqlQueryBuilder builder = new SqlQueryBuilder()
                .select("id", "name")
                .from("cars")
                .limit(10);

        Query query = builder.build();

        assertEquals("SELECT id, name FROM cars LIMIT $1", query.getSql());
        assertEquals(10, query.getParameters().get(0));
    }

    @Test
    void testOffset() {
        SqlQueryBuilder builder = new SqlQueryBuilder()
                .select("id", "name")
                .from("cars")
                .offset(5);

        Query query = builder.build();

        assertEquals("SELECT id, name FROM cars OFFSET $1", query.getSql());
        assertEquals(5, query.getParameters().get(0));
    }

    @Test
    void testCount() {
        SqlQueryBuilder builder = new SqlQueryBuilder()
                .select("id")
                .from("cars")
                .where("name = $", "Toyota");

        Query query = builder.count();

        assertEquals("SELECT COUNT(id) as total_rows FROM cars WHERE name = $1", query.getSql());
        assertEquals("Toyota", query.getParameters().get(0));
    }

    @Test
    void testInvalidTable() {
        SqlQueryBuilder builder = new SqlQueryBuilder();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            builder.from("invalid_table");
        });
        assertEquals("Invalid table name: invalid_table", exception.getMessage());
    }

    @Test
    void testNegativeLimit() {
        SqlQueryBuilder builder = new SqlQueryBuilder();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            builder.limit(-1);
        });
        assertEquals("Limit cannot be negative: -1", exception.getMessage());
    }

    @Test
    void testNegativeOffset() {
        SqlQueryBuilder builder = new SqlQueryBuilder();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            builder.offset(-1);
        });
        assertEquals("Offset cannot be negative: -1", exception.getMessage());
    }
}
