package com.pstag.services;

import java.util.List;
import java.util.Map;

import com.pstag.entities.CarEntity;
import com.pstag.repositories.CarRepository;
import com.pstag.utils.TotalRowsAndData;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CarService {

    public Uni<TotalRowsAndData<CarEntity>> findAll(PgPool client, Map<String, String> filters, String search,
            Map<String, String> sorts,
            int limit,
            int offset) {
        return CarRepository.findAll(client, filters, search, sorts, limit, offset);
    }

    public String getXml() {
        Multi<CarEntity> cars = CarRepository.getLatestSearchResult();
        List<CarEntity> carList = cars.collect().asList().await().indefinitely();
        return convertToXml(carList);
    }

    private String convertToXml(List<CarEntity> cars) {
        // Implement the conversion logic here
        // This is a placeholder implementation
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<cars>");
        for (CarEntity car : cars) {
            xmlBuilder.append("<car>");
            xmlBuilder.append("<id>").append(car.getId()).append("</id>");
            xmlBuilder.append("<make>").append(car.getMake()).append("</make>");
            xmlBuilder.append("<model>").append(car.getModel()).append("</model>");
            xmlBuilder.append("<year>").append(car.getTrimYear()).append("</year>");
            xmlBuilder.append("</car>");
        }
        xmlBuilder.append("</cars>");
        return xmlBuilder.toString();
    }
}
