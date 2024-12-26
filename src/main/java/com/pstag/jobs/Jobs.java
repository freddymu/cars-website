package com.pstag.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pstag.entities.CarEntity;
import com.pstag.entities.CarResponse;
import com.pstag.interfaces.MyAiService;
import com.pstag.repositories.CarRepository;
import com.pstag.utils.TotalRowsAndData;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.vertx.mutiny.pgclient.PgPool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class Jobs {

    private final PgPool client;
    private final MyAiService aiService;
    private final AtomicBoolean jobExecuted = new AtomicBoolean(false);

    @Inject
    public Jobs(PgPool client, MyAiService aiService) {
        this.client = client;
        this.aiService = aiService;
        Log.info("Jobs constructor called");
    }

    // @Scheduled(every = "1s", delayed = "3s", concurrentExecution = ConcurrentExecution.SKIP) // Schedule to run 3 seconds after startup
    public void fillMissingData() {
        if (jobExecuted.compareAndSet(false, true)) {
            Log.info("fillMissingData method called");
            performAsyncOperation()
                .subscribe().with(
                    success -> Log.info("Job executed successfully"),
                    failure -> Log.error("Job execution failed: " + failure)
                );
        } else {
            Log.info("Job already executed, skipping...");
        }
    }

    // @Scheduled(every = "1s", concurrentExecution = ConcurrentExecution.SKIP)
    private Uni<Void> performAsyncOperation() {
        Map<String, String> filters = new HashMap<>();
        filters.put("velocity", "0");
        int pageLimit = 3;

        // Calculate total pages
        return CarRepository.countTotalRows(client, filters, null) // Assume this returns Uni<Integer>
                .onItem().transform(totalRows -> totalRows / pageLimit) // Calculate total pages
                .flatMap(totalPage ->
                // Create a Multi of page numbers
                Multi.createFrom().range(1, totalPage + 1)
                        .onItem().transformToUniAndConcatenate(i -> {
                            int offset = (i - 1) * pageLimit; // Calculate offset
                            return CarRepository.findAll(client, filters, null, null, pageLimit, offset)
                                    .onItem().transform(carData -> {
                                        String concatenatedData = formatCarEntities(carData.getData());
                                        if (!concatenatedData.isEmpty()) {
                                            return Uni.createFrom().completionStage(() -> CompletableFuture
                                                    .supplyAsync(() -> aiService.getCarInformation(concatenatedData)));
                                        }
                                        return null; // In case of empty data
                                    })
                                    .onItem().invoke(response -> {
                                        if (response != null) {
                                            response.subscribe().with(this::handleCarInformation); // Perform car information handling
                                        } else {
                                            Log.info("No missing data found for page " + i);
                                        }
                                    });
                        })
                        .collect().asList() // Collect results into a list
                        .replaceWithVoid());
    }

    private String formatCarEntities(List<CarEntity> carEntities) {
        StringBuilder concatenatedDataBuilder = new StringBuilder();
        for (CarEntity car : carEntities) {
            concatenatedDataBuilder.append(car.toString()).append("\n");
        }
        return concatenatedDataBuilder.toString();
    }

    private void handleCarInformation(String concatenatedData) {
        // Fetch car information and update cars
        String response = aiService.getCarInformation(concatenatedData);
        Log.info(response);

        // Using ObjectMapper directly
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            CarResponse carEntities = objectMapper.readValue(response, new TypeReference<CarResponse>() {
            });
            carEntities.getCars()
                    .forEach(car -> CarRepository.updateCar(client, car.id(), car.color(), car.velocity())
                            .subscribe().with(
                                    updateResult -> {
                                        if (updateResult != null) {
                                            Log.info("Car with id " + updateResult.id() + " updated successfully");
                                        }
                                    },
                                    failure -> Log.error("Error updating car: " + failure.getMessage())));
        } catch (JsonProcessingException e) {
            Log.error("JSON Processing Error: " + e.getMessage());
        }
    }
}
