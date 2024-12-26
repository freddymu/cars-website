package com.pstag.repositories;

import io.quarkus.logging.Log;

import com.pstag.entities.CarEntity;

import java.util.ArrayList;
import java.util.Arrays;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import com.pstag.utils.SqlQueryBuilder;
import com.pstag.utils.SqlQueryBuilder.Query;
import com.pstag.utils.TotalRowsAndData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The CarRepository class provides methods to interact with the "cars" table in the database.
 * It includes methods for retrieving, updating, and counting car entities, as well as retrieving unique values for various car attributes.
 * 
 * Methods:
 * - findAll: Retrieves a paginated list of CarEntity objects based on filters, search criteria, and sorting options.
 * - updateCar: Updates the car entity with the specified ID in the database.
 * - getMakers: Retrieves a list of unique car makers from the database.
 * - getMakerAndModel: Retrieves a map of car makes and their corresponding models from the database.
 * - getTransmission: Retrieves a list of unique transmission types from the cars table.
 * - getColors: Retrieves a list of distinct car colors from the database.
 * - getBodyTypes: Retrieves a list of unique car body types from the database.
 * - getFuelTypes: Retrieves a list of unique fuel types from the cars table.
 * - getById: Retrieves a CarEntity by its ID from the database.
 * - updateCarColorAndImageUrl: Updates the color and image URL of a car in the database.
 * - countTotalRows: Counts the total number of rows in the "cars" table based on filters and search criteria.
 * 
 * Private Helper Methods:
 * - applyFilters: Applies the given filters to the provided SqlQueryBuilder.
 * - handleBetweenFilter: Adds a BETWEEN filter to the SQL query.
 * - handleInFilter: Adds an IN filter to the SQL query.
 * - handleDefaultFilter: Adds a default filter to the SQL query.
 * - applySearch: Applies the search criteria to the provided SqlQueryBuilder.
 * - applySort: Applies the sorting options to the provided SqlQueryBuilder.
 * - from: Converts a Row object to a CarEntity object.
 */
public class CarRepository {

    private CarRepository() {
        // Private constructor to hide the implicit public one
    }

    /**
     * Retrieves a paginated list of CarEntity objects from the database based on the provided filters, search criteria, and sorting options.
     * 
     * @param client  the PgPool client used to execute the database queries
     * @param filters a map of column names to filter values for filtering the results
     * @param search  a search string to filter the results based on a search criteria
     * @param sorts   a map of column names to sort directions (ASC/DESC) for sorting the results
     * @param limit   the maximum number of results to return
     * @param offset  the number of results to skip before starting to collect the result set
     * @return a Uni containing a TotalRowsAndData object which includes the total number of rows matching the criteria and a list of CarEntity objects
     */
    public static Uni<TotalRowsAndData<CarEntity>> findAll(PgPool client, Map<String, String> filters, String search,
            Map<String, String> sorts,
            int limit,
            int offset) {
        SqlQueryBuilder queryBuilder = new SqlQueryBuilder();
        queryBuilder.select(
                "id, make, model, trim_year, trim_name, trim_description, fuel_type, transmission, body_type, color, length, weight, velocity, image_url")
                .from("cars");

        applyFilters(queryBuilder, filters);
        applySearch(queryBuilder, search);
        applySort(queryBuilder, sorts);

        // copy queryBuilder to avoid modifying the original instance
        Query queryBuilderWithoutPagination = queryBuilder.count();

        // print query
        Log.info(queryBuilderWithoutPagination.getSql());
        Log.info(queryBuilderWithoutPagination.getParameters());

        Uni<Integer> totalRowsUni = client.preparedQuery(queryBuilderWithoutPagination.getSql())
                .execute(Tuple.from(queryBuilderWithoutPagination.getParameters()))
                .onItem().transform(rowSet -> {
                    if (rowSet.iterator().hasNext()) {
                        Row row = rowSet.iterator().next();
                        return row.getInteger("total_rows");
                    } else {
                        return 0;
                    }
                });

        if (limit > 0) {
            queryBuilder.limit(limit);
        }

        if (offset > 0) {
            queryBuilder.offset(offset);
        }

        // print query
        Query query = queryBuilder.build();
        Log.info(query.getSql());
        Log.info(query.getParameters());

        Uni<List<CarEntity>> dataUni = client.preparedQuery(query.getSql())
                .execute(Tuple.from(query.getParameters()))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(CarRepository::from)
                .collect().asList();

        return Uni.combine().all().unis(totalRowsUni, dataUni)
                .asTuple()
                .onItem().transform(tuple -> new TotalRowsAndData<>(tuple.getItem1(), tuple.getItem2()));
    }

    /**
     * Updates the car entity with the specified ID in the database.
     *
     * @param client  the PgPool client used to interact with the database
     * @param id      the ID of the car to be updated
     * @param colors  a list of colors to update the car's color field
     * @param velocity the new velocity to update the car's velocity field
     * @return a Uni containing the updated CarEntity
     */
    public static Uni<CarEntity> updateCar(PgPool client, Long id, List<String> colors, Double velocity) {

        List<CarEntity> result = client
                .preparedQuery("UPDATE cars SET color = $1, velocity = $2 WHERE id = $3 RETURNING *")
                .execute(Tuple.of(colors.toArray(new String[0]), velocity, id))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(CarRepository::from)
                .collect().asList()
                .await().indefinitely();

        return Uni.createFrom().item(result.get(0));
    }

    /**
     * Retrieves a list of unique car makers from the database.
     *
     * @param client the PgPool client used to execute the query
     * @return a Uni containing a list of car makers as strings
     */
    public static Uni<List<String>> getMakers(PgPool client) {
        return client.query("SELECT make FROM cars GROUP BY make ORDER BY make")
                .execute()
                .onItem().transform(rows -> {
                    List<String> bodyTypes = new ArrayList<>();
                    for (Row row : rows) {
                        bodyTypes.add(row.getString("make"));
                    }
                    return bodyTypes;
                });
    }

    /**
     * Retrieves a map of car makes and their corresponding models from the database.
     *
     * @param client the PgPool client used to execute the query
     * @return a Uni containing a map where the key is the car make and the value is a list of models for that make
     */
    public static Uni<Map<String, List<String>>> getMakerAndModel(PgPool client) {
        return client
                .query("SELECT make, model FROM cars GROUP BY make, model ORDER BY make, model") // Execute the query
                .execute() // Get a RowSet<Row>
                .onItem().transform(rows -> {
                    Map<String, List<String>> result = new HashMap<>();

                    for (Row row : rows) { // Use for loop to iterate through rows
                        String make = row.getString("make");
                        String model = row.getString("model");

                        // Collect models by make in the map
                        result.computeIfAbsent(make, k -> new ArrayList<>()).add(model);
                    }
                    return result; // Returning the populated map
                });
    }

    /**
     * Retrieves a list of unique transmission types from the cars table.
     *
     * @param client the PgPool client used to execute the query
     * @return a Uni containing a list of transmission types, ordered alphabetically
     */
    public static Uni<List<String>> getTransmission(PgPool client) {
        return client.query("SELECT transmission FROM cars GROUP BY transmission ORDER BY transmission")
                .execute()
                .onItem().transform(rows -> {
                    List<String> transmissions = new ArrayList<>();
                    for (Row row : rows) {
                        transmissions.add(row.getString("transmission"));
                    }
                    return transmissions;
                });
    }

    /**
     * Retrieves a list of distinct car colors from the database.
     *
     * @param client the PgPool client used to execute the query
     * @return a Uni containing a list of distinct car colors
     */
    public static Uni<List<String>> getColors(PgPool client) {
        return client.query("SELECT color FROM cars WHERE color IS NOT NULL GROUP BY color ORDER BY color")
                .execute()
                .onItem().transform(rows -> {
                    List<String> colors = new ArrayList<>();
                    for (Row row : rows) {
                        colors.addAll(Arrays.asList(row.getArrayOfStrings("color")));
                    }
                    return colors.stream().distinct().toList();
                });
    }

    /**
     * Retrieves a list of unique car body types from the database.
     *
     * @param client the PgPool client used to execute the query
     * @return a Uni containing a list of unique car body types, ordered alphabetically
     */
    public static Uni<List<String>> getBodyTypes(PgPool client) {
        return client.query("SELECT body_type FROM cars GROUP BY body_type ORDER BY body_type")
                .execute()
                .onItem().transform(rows -> {
                    List<String> bodyTypes = new ArrayList<>();
                    for (Row row : rows) {
                        bodyTypes.add(row.getString("body_type"));
                    }
                    return bodyTypes;
                });
    }

    /**
     * Retrieves a list of unique fuel types from the cars table in the database.
     *
     * @param client the PgPool client used to execute the query
     * @return a Uni containing a list of unique fuel types as strings
     */
    public static Uni<List<String>> getFuelTypes(PgPool client) {
        return client.query("SELECT fuel_type FROM cars GROUP BY fuel_type ORDER BY fuel_type")
                .execute()
                .onItem().transform(rows -> {
                    List<String> fuelTypes = new ArrayList<>();
                    for (Row row : rows) {
                        fuelTypes.add(row.getString("fuel_type"));
                    }
                    return fuelTypes;
                });
    }

    /**
     * Retrieves a CarEntity by its ID from the database.
     *
     * @param client the PgPool client used to execute the query
     * @param id the ID of the car to retrieve
     * @return a Uni containing the CarEntity if found, or null if not found
     */
    public static Uni<CarEntity> getById(PgPool client, Long id) {
        return client.preparedQuery("SELECT * FROM cars WHERE id = $1")
                .execute(Tuple.of(id))
                .onItem()
                .transformToUni(set -> set.iterator().hasNext() ? Uni.createFrom().item(from(set.iterator().next()))
                        : Uni.createFrom().nullItem());
    }

    /**
     * Updates the color and image URL of a car in the database.
     *
     * @param client   the PgPool client used to interact with the database
     * @param id       the ID of the car to be updated
     * @param colors   a list of new colors to be set for the car
     * @param imageUrls a list of new image URLs to be set for the car
     * @return a Uni containing the updated CarEntity
     */
    public static Uni<CarEntity> updateCarColorAndImageUrl(PgPool client, Long id, List<String> colors,
            List<String> imageUrls) {
        List<CarEntity> result = client
                .preparedQuery("UPDATE cars SET color = $1, image_url = $2 WHERE id = $3 RETURNING *")
                .execute(Tuple.of(colors.toArray(new String[0]), imageUrls.toArray(new String[0]), id))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(CarRepository::from)
                .collect().asList()
                .await().indefinitely();

        return Uni.createFrom().item(result.get(0));
    }

    /**
     * Counts the total number of rows in the "cars" table based on the provided filters and search criteria.
     *
     * @param client the PgPool client used to execute the query
     * @param filters a map of filters to apply to the query
     * @param search a search string to apply to the query
     * @return a Uni containing the total number of rows that match the filters and search criteria
     */
    public static Uni<Integer> countTotalRows(PgPool client, Map<String, String> filters, String search) {
        SqlQueryBuilder queryBuilder = new SqlQueryBuilder();
        queryBuilder.select("COUNT(*) AS total_rows")
                .from("cars");

        applyFilters(queryBuilder, filters);
        applySearch(queryBuilder, search);

        Query query = queryBuilder.build();

        return client.preparedQuery(query.getSql())
                .execute(Tuple.from(query.getParameters()))
                .onItem().transform(rowSet -> {
                    if (rowSet.iterator().hasNext()) {
                        Row row = rowSet.iterator().next();
                        return row.getInteger("total_rows");
                    } else {
                        return 0;
                    }
                });
    }

    /**
     * Applies the given filters to the provided SqlQueryBuilder.
     *
     * @param queryBuilder the SqlQueryBuilder to which the filters will be applied
     * @param filters a map of filter field names to their corresponding filter values
     *
     * The method processes each filter entry and applies the appropriate filter to the query builder.
     * It supports "between" and "in" filters, as well as default filters.
     * 
     * Field names are converted from camelCase to snake_case before being applied.
     * If a field name is not valid (i.e., not present in CarEntity.getFields()), it is ignored.
     */
    private static void applyFilters(SqlQueryBuilder queryBuilder, Map<String, String> filters) {

        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String fieldName = entry.getKey().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                String originalValue = entry.getValue();

                if (!CarEntity.getFields().contains(fieldName)) {
                    // throw new IllegalArgumentException("Invalid filter field: " + fieldName);
                    continue;
                }                

                if (originalValue != null && originalValue.toLowerCase().contains("between(")) {
                    handleBetweenFilter(queryBuilder, fieldName, originalValue);
                } else if (originalValue != null && originalValue.toLowerCase().contains("in(")) {
                    handleInFilter(queryBuilder, fieldName, originalValue);
                } else {
                    handleDefaultFilter(queryBuilder, fieldName, originalValue);
                }
            }
        }
    }

    /**
     * Adds a BETWEEN filter to the SQL query based on the provided key and value.
     *
     * @param queryBuilder the SQL query builder to which the filter will be added
     * @param snakeCaseKey the key in snake_case format to be used in the SQL query
     * @param originalValue the value containing the range for the BETWEEN filter in the format "between (min,max)"
     */
    private static void handleBetweenFilter(SqlQueryBuilder queryBuilder, String snakeCaseKey, String originalValue) {
        String[] values = originalValue.split("between");
        String[] rangeValues = values[1].replaceAll("[()]", "").split(",");
        if (rangeValues.length == 2) {
            queryBuilder.where(String.format(" %s BETWEEN $ AND $ ", snakeCaseKey),
                    Double.parseDouble(rangeValues[0].trim()), Double.parseDouble(rangeValues[1].trim()));
        }
    }

    /**
     * Handles the "IN" filter for SQL queries by parsing the provided filter value and adding the appropriate
     * condition to the query builder.
     *
     * @param queryBuilder   The SqlQueryBuilder instance to which the "IN" condition will be added.
     * @param snakeCaseKey   The column name in snake_case format to be used in the SQL query.
     * @param originalValue  The original filter value in the format "in(value1,value2,...)". The values will be parsed
     *                       and used in the "IN" condition of the SQL query.
     * @throws IllegalArgumentException if the parsed values are invalid or empty.
     */
    private static void handleInFilter(SqlQueryBuilder queryBuilder, String snakeCaseKey, String originalValue) {
        String[] values = originalValue.split("in");
        String[] inValues = values[1].replaceAll("[()]", "").split(",");
        String placeholder = String.join(",", Collections.nCopies(inValues.length, " $ "));
        Object[] parsedValues = CarEntity.parse(snakeCaseKey, inValues);
        if (parsedValues.length == 0) {
            throw new IllegalArgumentException("Invalid IN filter value: " + originalValue);
        }
        if (parsedValues[0] instanceof String) {
            queryBuilder.where(String.format(" LOWER(%s) IN ( %s ) ", snakeCaseKey, placeholder),
                    Arrays.stream(parsedValues).map(value -> value.toString().toLowerCase()).toArray());
        } else {
            queryBuilder.where(String.format(" %s IN ( %s ) ", snakeCaseKey, placeholder), parsedValues);
        }
    }

    /**
     * Handles the default filter for the given query builder based on the provided key and value.
     * 
     * @param queryBuilder the SQL query builder to which the filter will be applied
     * @param snakeCaseKey the key in snake_case format to be used in the filter
     * @param originalValue the original value to be parsed and used in the filter
     * 
     * If the original value is not null, it parses the value using CarEntity.parse method and determines
     * the appropriate SQL operator (ILIKE for strings, = for other types). If the key is "color" and the value
     * is a list, it constructs an SQL condition to check if the key's value is within the provided list.
     * Otherwise, it constructs a standard SQL condition using the key, operator, and parsed value.
     * If the original value is null, it adds a condition to check if the key's value is NULL.
     */
    private static void handleDefaultFilter(SqlQueryBuilder queryBuilder, String snakeCaseKey, String originalValue) {
        if (originalValue != null) {
            Object value = CarEntity.parse(snakeCaseKey, originalValue);
            String operator = (value instanceof String) ? "ILIKE" : "=";
            if (snakeCaseKey.equals("color")) {
                if (value instanceof List<?>) {
                    List<String> stringList = ((List<?>) value).stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .map(s -> "'" + s + "'")
                            .toList();
                    queryBuilder.where(
                            String.format("%s && ARRAY[%s]::VARCHAR[]", snakeCaseKey, String.join(",", stringList)));
                }
            } else {
                queryBuilder.where(String.format("%s %s $ ", snakeCaseKey, operator), value);
            }
        } else {
            queryBuilder.where(String.format("%s IS NULL", snakeCaseKey));
        }
    }

    /**
     * Applies a search filter to the given SqlQueryBuilder based on the provided search string.
     * If the search string is not null and not empty, it adds a condition to the query
     * to match the fulltext_search column using a case-insensitive LIKE operation.
     *
     * @param queryBuilder the SqlQueryBuilder to which the search filter will be applied
     * @param search the search string used to filter the results; if null or empty, no filter is applied
     */
    private static void applySearch(SqlQueryBuilder queryBuilder, String search) {
        if (search != null && !search.isEmpty()) {
            queryBuilder.where(
                    "(fulltext_search ILIKE $)",
                    "%" + search.replace(" ", "%") + "%");
        }
    }

    /**
     * Applies sorting to the given SQL query builder based on the provided sort map.
     *
     * @param queryBuilder the SQL query builder to which sorting will be applied
     * @param sort a map containing field names as keys and sort order values ("asc" or "desc") as values
     * @throws IllegalArgumentException if a sort order value is invalid (not "asc" or "desc")
     */
    private static void applySort(SqlQueryBuilder queryBuilder, Map<String, String> sort) {
        if (sort != null && !sort.isEmpty()) {
            for (Map.Entry<String, String> entry : sort.entrySet()) {
                String fieldName = entry.getKey().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                String orderValue = entry.getValue().trim();

                if (!CarEntity.getFields().contains(fieldName)) {
                    // throw new IllegalArgumentException("Invalid filter field: " + fieldName);
                    continue;
                }                

                if (!orderValue.equalsIgnoreCase("asc") && !orderValue.equalsIgnoreCase("desc")) {
                    throw new IllegalArgumentException("Invalid sort order value: " + orderValue);
                }

                queryBuilder.orderBy(fieldName + " " + orderValue);
            }
        }
    }

    /**
     * Converts a database row into a CarEntity object.
     *
     * @param row the database row to convert
     * @return a CarEntity object populated with data from the row
     */
    private static CarEntity from(Row row) {
        return new CarEntity.Builder()
                .id(row.getLong("id"))
                .make(row.getString("make"))
                .model(row.getString("model"))
                .trimYear(row.getInteger("trim_year"))
                .trimName(row.getString("trim_name"))
                .trimDescription(row.getString("trim_description"))
                .fuelType(row.getString("fuel_type"))
                .transmission(row.getString("transmission"))
                .bodyType(row.getString("body_type"))
                .color(row.getArrayOfStrings("color") != null
                        ? Arrays.asList(row.getArrayOfStrings("color"))
                        : null)
                .length(row.getDouble("length") != null ? row.getDouble("length") : 0.0)
                .weight(row.getDouble("weight") != null ? row.getDouble("weight") : 0.0)
                .velocity(row.getDouble("velocity") != null ? row.getDouble("velocity") : 0.0)
                .imageUrl(row.getArrayOfStrings("image_url") != null
                        ? Arrays.asList(row.getArrayOfStrings("image_url"))
                        : null)
                .build();
    }
}
