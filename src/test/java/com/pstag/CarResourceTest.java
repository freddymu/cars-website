package com.pstag;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class CarResourceTest {
    // Test for a successful search of cars with valid criteria
    @Test
    void testCarSearchEndpoint() {
        given()
                .queryParam("length", 4.5)
                .queryParam("weight", 1500)
                .queryParam("velocity", 200)
                .queryParam("color", "Red")
                .when().get("/api/cars/search")
                .then()
                .statusCode(200)
                .contentType("application/json") // Assuming the response is in JSON
                .body(containsString("['id', 'length', 'weight', 'velocity', 'color']")); // Example structure
    }

    // Test for searching cars with no results
    @Test
    void testCarSearchNoResults() {
        given()
                .queryParam("length", 10.0) // Unrealistic length to ensure no results found
                .queryParam("weight", 3000)
                .queryParam("velocity", 300)
                .queryParam("color", "Invisible")
                .when().get("/api/cars/search")
                .then()
                .statusCode(204); // No Content status code if no cars match criteria
    }

    // Test for the download endpoint that returns XML
    @Test
    void testCarDownloadEndpoint() {
        given()
                .queryParam("length", 4.5)
                .queryParam("weight", 1500)
                .queryParam("velocity", 200)
                .queryParam("color", "Red")
                .when().get("/api/cars/download")
                .then()
                .statusCode(200)
                .contentType("application/xml")
                .body(containsString("<cars>")); // Assuming XML starts with <cars>
    }

    // Test for invalid search criteria
    @Test
    void testCarSearchInvalidInput() {
        given()
                .queryParam("length", "invalidLength") // Pass invalid type
                .queryParam("weight", -1500) // Invalid weight
                .queryParam("velocity", 150)
                .queryParam("color", "Red")
                .when().get("/api/cars/search")
                .then()
                .statusCode(400) // Bad Request for invalid parameters
                .body(containsString("error")); // Assuming the error message is returned
    }

    // Test that ensures proper response structure for search
    @Test
    void testCarSearchResponseStructure() {
        given()
                .queryParam("length", 4.5)
                .queryParam("weight", 1500)
                .queryParam("velocity", 200)
                .queryParam("color", "Red")
                .when().get("/api/cars/search")
                .then()
                .statusCode(200)
                .body("car[0].id", is(1)) // Assuming the first car has id 1
                .body("car[0].length", is(4.5F))
                .body("car[0].color", is("Red"));
    }

}
