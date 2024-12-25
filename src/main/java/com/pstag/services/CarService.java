package com.pstag.services;

import java.io.IOException;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

// import javax.lang.model.util.Elements;

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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

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

    public Uni<GenericResponse<Map<String, Object>>> getUiParams(PgPool client) {
        Map<String, Uni<?>> uniMap = new HashMap<>();

        // Initialize the Uni instances for each parameter
        uniMap.put("makers", CarRepository.getMakers(client));
        uniMap.put("makersAndModels", CarRepository.getMakerAndModel(client));
        uniMap.put("transmissions", CarRepository.getTransmission(client));
        uniMap.put("colors", CarRepository.getColors(client));
        uniMap.put("fuelTypes", CarRepository.getFuelTypes(client));
        uniMap.put("bodyTypes", CarRepository.getBodyTypes(client));

        // Combine all Uris into a single Uni
        return Uni.combine().all().unis(uniMap.values()).collectFailures()
                .combinedWith(results -> {
                    Map<String, Object> response = new HashMap<>();
                    int i = 0;
                    for (String key : uniMap.keySet()) {
                        response.put(key, results.get(i++)); // Map results to corresponding keys
                    }
                    return new GenericResponse<>(response, "UI parameters fetched successfully");
                });
    }

    public Uni<GenericResponse<Map<String, String>>> getImage(PgPool client, Long id) {
        Uni<CarEntity> carUni = CarRepository.getById(client, id);
        return carUni.onItem().transformToUni(car -> {
            if (car == null) {
                return Uni.createFrom().item(new GenericResponse<>(null, "Car not found"));
            }
            List<String> colors = car.getColor();
            List<String> urls = car.getImageUrl();

            if (colors == null || colors.isEmpty()) {
                colors = List.of("Black", "White", "Gray", "Silver", "Blue", "Red");
            }

            if (urls != null && !urls.isEmpty()) {
                Map<String, String> result = new HashMap<>();
                for (int i = 0; i < colors.size(); i++) {
                    result.put(colors.get(i), urls.get(i));
                }
                Log.info("Image fetched from database");
                return Uni.createFrom().item(new GenericResponse<>(result, "Image fetched successfully"));
            }

            List<Uni<Map.Entry<String, String>>> imageUnis = colors.stream()
                    .map(color -> {
                        String keyword = "Car " + car.getTrimYear() + " " + car.getMake() + " " + car.getModel() + " " + car.getTrimName() + " " + color;
                        return fetchImageUrl(keyword)
                                .onItem().transform(url -> Map.entry(color, url));
                    })
                    .toList();

            return Uni.combine().all().unis(imageUnis)
                    .combinedWith(entries -> {
                        Map<String, String> result = new HashMap<>();
                        for (Object entry : entries) {
                            Map.Entry<String, String> mapEntry = (Map.Entry<String, String>) entry;
                            result.put(mapEntry.getKey(), mapEntry.getValue());
                        }

                        List<String> colorList = new ArrayList<>(result.keySet());
                        List<String> urlList = new ArrayList<>(result.values());
                        CarRepository.updateCarColorAndImageUrl(client, id, colorList, urlList);

                        return new GenericResponse<>(result, "Image fetched successfully");
                    });
        });
    }

    private Uni<String> fetchImageUrl(String keyword) {
        return Uni.createFrom().item(() -> encodeKeyword(keyword))
                .onItem().transformToUni(this::fetchSearchUrl)
                .onItem().transformToUni(this::fetchImageFromUrl);
    }

    private String encodeKeyword(String keyword) {
        try {
            String encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8");
            String searchUrl = "https://www.google.com/search?q=" + encodedKeyword + "&tbs=isz:l&biw=1528&bih=738&dpr=1.25&tbm=isch&hl=en";
            Log.info(searchUrl);
            return searchUrl;
        } catch (java.io.UnsupportedEncodingException e) {
            Log.error("Error encoding URL", e);
            return null;
        }
    }

    private Uni<String> fetchSearchUrl(String searchUrl) {
        if (searchUrl == null) {
            return Uni.createFrom().nullItem();
        }

        String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
        };
        String userAgent = userAgents[new java.util.Random().nextInt(userAgents.length)];

        return Uni.createFrom().completionStage(() -> {
            try {
                return java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                        .build()
                        .sendAsync(java.net.http.HttpRequest.newBuilder()
                                .uri(new java.net.URI(searchUrl))
                                .header("User-Agent", userAgent)
                                .build(), java.net.http.HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> response.body());
            } catch (Exception e) {
                Log.error("Error fetching search URL", e);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
    }

    private Uni<String> fetchImageFromUrl(String responseBody) {
        if (responseBody == null) {
            return Uni.createFrom().nullItem();
        }

        Document doc = Jsoup.parse(responseBody);
        Element firstImage = doc.select("img[src^=https]").first();
        if (firstImage != null) {
            Log.info(firstImage.attr("src"));
        }
        return Uni.createFrom().item(firstImage != null ? firstImage.attr("src") : "");
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
