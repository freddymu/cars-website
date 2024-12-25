package com.pstag.repositories;

import io.quarkus.logging.Log;

import com.pstag.entities.CarEntity;
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

    public static Multi<CarEntity> getLatestSearchResult() {
        // Implement the method here
        return null;
    }

    public static CarEntity updateCar(PgPool client, Long id, List<String> colors, Double velocity) {

        List<CarEntity> result = client
                .preparedQuery("UPDATE cars SET color = $1, velocity = $2 WHERE id = $3 RETURNING *")
                .execute(Tuple.of(colors.toArray(new String[0]), velocity, id))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(CarRepository::from)
                .collect().asList()
                .await().indefinitely();

        return result.get(0);
    }

    private static void applyFilters(SqlQueryBuilder queryBuilder, Map<String, String> filters) {
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String snakeCaseKey = entry.getKey().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
                String originalValue = entry.getValue();

                if (originalValue != null && originalValue.toLowerCase().contains("between")) {
                    handleBetweenFilter(queryBuilder, snakeCaseKey, originalValue);
                } else if (originalValue != null && originalValue.toLowerCase().contains("in")) {
                    handleInFilter(queryBuilder, snakeCaseKey, originalValue);
                } else {
                    handleDefaultFilter(queryBuilder, snakeCaseKey, originalValue);
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
            queryBuilder.where(String.format("%s %s $ ", snakeCaseKey, operator), value);
        } else {
            queryBuilder.where(String.format("%s IS NULL", snakeCaseKey));
        }
    }

    private static void applySearch(SqlQueryBuilder queryBuilder, String search) {
        if (search != null && !search.isEmpty()) {
            queryBuilder.where(
                    "(make ILIKE $ OR model ILIKE $ OR trim_name ILIKE $ OR trim_description ILIKE $)",
                    "%" + search + "%");
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
