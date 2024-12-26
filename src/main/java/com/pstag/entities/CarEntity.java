package com.pstag.entities;

import java.util.List;
import java.util.Arrays;

public class CarEntity {
    private Long id;
    private String make;
    private String model;
    private int trimYear;
    private String trimName;
    private String trimDescription;
    private String fuelType;
    private String transmission;
    private String bodyType;
    private List<String> color;
    private double length;
    private double weight;
    private double velocity;
    private List<String> imageUrl;
    private List<ExteriorColor> exteriorColors;

    public CarEntity() {
        // Default constructor for Jackson
    }

    public List<ExteriorColor> getExteriorColors() {
        return exteriorColors;
    }

    public void setExteriorColors(List<ExteriorColor> exteriorColors) {
        this.exteriorColors = exteriorColors;
    }

    // Constructor
    private CarEntity(Builder builder) {
        this.id = builder.id;
        this.make = builder.make;
        this.model = builder.model;
        this.trimYear = builder.trimYear;
        this.trimName = builder.trimName;
        this.trimDescription = builder.trimDescription;
        this.fuelType = builder.fuelType;
        this.transmission = builder.transmission;
        this.bodyType = builder.bodyType;
        this.color = builder.color;
        this.length = builder.length;
        this.weight = builder.weight;
        this.velocity = builder.velocity;
        this.imageUrl = builder.imageUrl;
    }

    // Builder class
    public static class Builder {
        private Long id;
        private String make;
        private String model;
        private int trimYear;
        private String trimName;
        private String trimDescription;
        private String fuelType;
        private String transmission;
        private String bodyType;
        private List<String> color;
        private double length;
        private double weight;
        private double velocity;
        private List<String> imageUrl;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder make(String make) {
            this.make = make;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder trimYear(int trimYear) {
            this.trimYear = trimYear;
            return this;
        }

        public Builder trimName(String trimName) {
            this.trimName = trimName;
            return this;
        }

        public Builder trimDescription(String trimDescription) {
            this.trimDescription = trimDescription;
            return this;
        }

        public Builder fuelType(String fuelType) {
            this.fuelType = fuelType;
            return this;
        }

        public Builder transmission(String transmission) {
            this.transmission = transmission;
            return this;
        }

        public Builder bodyType(String bodyType) {
            this.bodyType = bodyType;
            return this;
        }

        public Builder color(List<String> color) {
            this.color = color;
            return this;
        }

        public Builder length(double length) {
            this.length = length;
            return this;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public Builder velocity(double velocity) {
            this.velocity = velocity;
            return this;
        }

        public Builder imageUrl(List<String> imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public CarEntity build() {
            return new CarEntity(this);
        }
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public int getTrimYear() {
        return trimYear;
    }

    public String getTrimName() {
        return trimName;
    }

    public String getTrimDescription() {
        return trimDescription;
    }

    public String getFuelType() {
        return fuelType;
    }

    public String getTransmission() {
        return transmission;
    }

    public String getBodyType() {
        return bodyType;
    }

    public List<String> getColor() {
        return color;
    }

    public double getLength() {
        return length;
    }

    public double getWeight() {
        return weight;
    }

    public double getVelocity() {
        return velocity;
    }

    public List<String> getImageUrl() {
        return imageUrl;
    }

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