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

public class CarRepository {

    private CarRepository() {
        // Private constructor to hide the implicit public one
    }

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

    public static Uni<CarEntity> getById(PgPool client, Long id) {
        return client.preparedQuery("SELECT * FROM cars WHERE id = $1")
                .execute(Tuple.of(id))
                .onItem()
                .transformToUni(set -> set.iterator().hasNext() ? Uni.createFrom().item(from(set.iterator().next()))
                        : Uni.createFrom().nullItem());
    }

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

    private static void applyFilters(SqlQueryBuilder queryBuilder, Map<String, String> filters) {

        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String fieldName = entry.getKey().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                String originalValue = entry.getValue();

                // check the field name. The field name should be available in CarEntity properties
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

    private static void handleBetweenFilter(SqlQueryBuilder queryBuilder, String snakeCaseKey, String originalValue) {
        String[] values = originalValue.split("between");
        String[] rangeValues = values[1].replaceAll("[()]", "").split(",");
        if (rangeValues.length == 2) {
            queryBuilder.where(String.format(" %s BETWEEN $ AND $ ", snakeCaseKey),
                    Double.parseDouble(rangeValues[0].trim()), Double.parseDouble(rangeValues[1].trim()));
        }
    }

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

    private static void applySearch(SqlQueryBuilder queryBuilder, String search) {
        if (search != null && !search.isEmpty()) {
            queryBuilder.where(
                    "(fulltext_search ILIKE $)",
                    "%" + search.replace(" ", "%") + "%");
        }
    }

    private static void applySort(SqlQueryBuilder queryBuilder, Map<String, String> sort) {
        if (sort != null && !sort.isEmpty()) {
            for (Map.Entry<String, String> entry : sort.entrySet()) {
                String snakeCaseKey = entry.getKey().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                String orderValue = entry.getValue().trim();

                if (!orderValue.equalsIgnoreCase("asc") && !orderValue.equalsIgnoreCase("desc")) {
                    throw new IllegalArgumentException("Invalid sort order value: " + orderValue);
                }

                queryBuilder.orderBy(snakeCaseKey + " " + orderValue);
            }
        }
    }

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
