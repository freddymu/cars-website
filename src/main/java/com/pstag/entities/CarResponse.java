package com.pstag.entities;

import java.util.List;

public class CarResponse {
    private List<CarEntity> cars;

    // Getters and Setters
    public List<CarEntity> getCars() {
        return cars;
    }

    public void setCars(List<CarEntity> cars) {
        this.cars = cars;
    }
}
