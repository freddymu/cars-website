package com.pstag;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class CarControllerTest {

    @Test
    void testGetCarsEndpoint() {
        given()
                .queryParam("search", "sedan")
                .queryParam("limit", 5)
                .queryParam("offset", 0)
                .when().get("/api/cars")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("data.size()", is(5));
    }

    @Test
    void testGetCarsWithFiltersEndpoint() {
        given()
                .queryParam("filter[make]", "Toyota")
                .queryParam("filter[trimYear]", "2020")
                .when().get("/api/cars")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("data.size()", is(10))
                .body("data[0].make", is("Toyota"))
                .body("data[0].trimYear", is(2020));
    }

    @Test
    void testGetCarsWithSortsEndpoint() {
        given()
                .queryParam("sort[make]", "asc")
                .when().get("/api/cars")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("data.size()", is(10)); // Assuming default limit is 10
    }

    @Test
    void testGetCarsWithXmlEndpoint() {
        given()
                .queryParam("filter[trimYear]", "2020")
                .queryParam("search", "sedan")
                .when().get("/api/cars/xml")
                .then()
                .statusCode(200)
                .contentType("application/xml")
                .body(containsString("<bodyType>Sedan</bodyType>"));
    }
}
