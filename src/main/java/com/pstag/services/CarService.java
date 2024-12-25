package com.pstag.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pstag.entities.CarEntity;
import com.pstag.entities.CarResponse;
import com.pstag.interfaces.MyAiService;
import com.pstag.repositories.CarRepository;
import com.pstag.utils.TotalRowsAndData;
import com.pstag.utils.GenericResponse;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;

@ApplicationScoped
public class CarService {

    private final MyAiService aiService;

    @Inject
    public CarService(MyAiService aiService) {
        this.aiService = aiService;
    }

    public Uni<TotalRowsAndData<CarEntity>> findAll(PgPool client, Map<String, String> filters, String search,
            Map<String, String> sorts,
            int limit,
            int offset) {
        return CarRepository.findAll(client, filters, search, sorts, limit, offset);
    }

    public String getXml(PgPool client, Map<String, String> filters, Map<String, String> sorts, String search) {
        Uni<TotalRowsAndData<CarEntity>> result = CarRepository.findAll(client, filters, search, sorts, 0, 0);
        List<CarEntity> carList = result.onItem().transform(TotalRowsAndData::getData).await().indefinitely();
        return convertToXml(carList);
    }

    public GenericResponse<String> fillMissingData(PgPool client) {

        Map<String, String> filters = new HashMap<>();
        filters.put("make", "Honda");
        filters.put("velocity", null);

        int pageLimit = 3;
        int totalPage = 15688 / pageLimit;

        for (int i = 1; i <= totalPage; i++) {
            int offset = (i - 1) * pageLimit;

            Uni<TotalRowsAndData<CarEntity>> result = CarRepository.findAll(client, filters, null, null, pageLimit,
                    offset);

            String concatenatedData = result.onItem().transform(totalRowsAndData -> {
                List<CarEntity> carEntities = totalRowsAndData.getData();
                StringBuilder concatenatedDataBuilder = new StringBuilder();
                for (CarEntity car : carEntities) {
                    concatenatedDataBuilder.append(car.toString()).append("\n");
                }
                return concatenatedDataBuilder.toString();
            }).await().indefinitely();

            ObjectMapper objectMapper = new ObjectMapper();

            String response = aiService.getCarInformation(concatenatedData);

            Log.info(response);

            try {
                CarResponse carEntities = objectMapper.readValue(response, new TypeReference<CarResponse>() {
                });
                for (CarEntity car : carEntities.getCars()) {
                    // Log.info(objectMapper.writeValueAsString(car.getExteriorColors()));
                    // Log.info(car.toString());

                    CarEntity updateResult = CarRepository.updateCar(client, car.getId(), car.getColor(),
                            car.getVelocity());
                    if (updateResult != null) {
                        Log.info("Car with id " + updateResult.getId() + " updated successfully");
                    }
                }
            } catch (JsonProcessingException e) {
                // throw new RuntimeException("Error processing JSON", e);
            }

        }

        return new GenericResponse<>("Missing data filled successfully",
                "Missing data filled successfully");
    }

    private String convertToXml(List<CarEntity> cars) {
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<cars>");
        for (CarEntity car : cars) {
            xmlBuilder.append("<car>");
            xmlBuilder.append("<id>").append(car.getId()).append("</id>");
            xmlBuilder.append("<make>").append(car.getMake()).append("</make>");
            xmlBuilder.append("<model>").append(car.getModel()).append("</model>");
            xmlBuilder.append("<year>").append(car.getTrimYear()).append("</year>");
            xmlBuilder.append("<trimName>").append(car.getTrimName()).append("</trimName>");
            xmlBuilder.append("<trimDescription>").append(car.getTrimDescription()).append("</trimDescription>");
            xmlBuilder.append("<color>").append(car.getColor()).append("</color>");
            xmlBuilder.append("<length>").append(car.getLength()).append("</length>");
            xmlBuilder.append("<weight>").append(car.getWeight()).append("</weight>");
            xmlBuilder.append("<velocity>").append(car.getVelocity()).append("</velocity>");
            xmlBuilder.append("</car>");
        }
        xmlBuilder.append("</cars>");
        return xmlBuilder.toString();
    }
}
