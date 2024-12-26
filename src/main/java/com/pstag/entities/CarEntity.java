package com.pstag.entities;

import java.util.List;
import java.util.Arrays;

public record CarEntity(
    Long id,
    String make,
    String model,
    int trimYear,
    String trimName,
    String trimDescription,
    String fuelType,
    String transmission,
    String bodyType,
    List<String> color,
    double length,
    double weight,
    double velocity,
    List<String> imageUrl,
    List<ExteriorColor> exteriorColors
) {

    public static Object parse(String fieldName, String value) {
        switch (fieldName) {
            case "id":
                return Long.parseLong(value);
            case "trim_year":
                return Integer.parseInt(value);
            case "length", "weight", "velocity":
                return Double.parseDouble(value);
            case "color", "image_url":
                return Arrays.asList(value.split(","));
            default:
                return value;
        }
    }

    public static Object[] parse(String fieldName, String[] value) {

        switch (fieldName) {
            case "id":
                return Arrays.stream(value).map(Long::parseLong).toArray();
            case "trim_year":
                return Arrays.stream(value).map(Integer::parseInt).toArray();
            case "length", "weight", "velocity":
                return Arrays.stream(value).map(Double::parseDouble).toArray();
            case "color", "image_url":
                return Arrays.stream(value).map(val -> Arrays.asList(val.split(","))).toArray();
            default:
                return value;
        }

    }

    @Override
    public String toString() {
        return "CarEntity{" +
                "id=" + id +
                ", make='" + make + '\'' +
                ", model='" + model + '\'' +
                ", trimYear=" + trimYear +
                ", trimName='" + trimName + '\'' +
                ",trimDescription='" + trimDescription + '\'' +
                ", fuelType='" + fuelType + '\'' +
                ", transmission='" + transmission + '\'' +
                ", bodyType='" + bodyType + '\'' +
                ", color=" + color +
                ", length=" + length +
                ", weight=" + weight +
                ", velocity=" + velocity +
                ", imageUrl=" + imageUrl +
                '}';
    }

    public static List<String> getFields() {
        return Arrays.asList("id", "make", "model", "trim_year", "trim_name", "trim_description", "fuel_type", "transmission", "body_type", "color", "length", "weight", "velocity");
    }
}