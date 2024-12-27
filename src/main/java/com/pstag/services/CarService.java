package com.pstag.services;

import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.time.LocalDateTime;
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
import reactor.core.publisher.Mono;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.models.BlockBlobItem;
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

    private final BlobServiceAsyncClient blobServiceAsyncClient;

    private final String useAzureBlob;

    @Inject
    public CarService(MyAiService aiService, BlobServiceAsyncClient blobServiceAsyncClient) {
        this.aiService = aiService;
        this.blobServiceAsyncClient = blobServiceAsyncClient;

        useAzureBlob = ConfigProvider.getConfig().getValue("quarkus.azure.storage.blob.enabled",
                String.class);
    }

    /**
     * Retrieves a paginated list of CarEntity objects based on the provided
     * filters, search criteria, and sorting options.
     *
     * @param client  the PgPool client used to interact with the database
     * @param filters a map of filter criteria to apply to the query
     * @param search  a search string to filter the results
     * @param sorts   a map of sorting options to apply to the query
     * @param limit   the maximum number of results to return
     * @param offset  the starting point in the list of results
     * @return a Uni containing a TotalRowsAndData object with the total number of
     *         rows and the list of CarEntity objects
     */
    public Uni<TotalRowsAndData<CarEntity>> findAll(PgPool client, Map<String, String> filters, String search,
            Map<String, String> sorts,
            int limit,
            int offset) {
        return CarRepository.findAll(client, filters, search, sorts, limit, offset);
    }

    /**
     * Retrieves a list of CarEntity objects from the database based on the provided
     * filters, sorts, and search criteria,
     * and converts the list to an XML string.
     *
     * @param client  the PgPool client used to interact with the database
     * @param filters a map of filters to apply to the query
     * @param sorts   a map of sorting options to apply to the query
     * @param search  a search string to filter the results
     * @return an XML string representation of the list of CarEntity objects
     */
    public String getXml(PgPool client, Map<String, String> filters, Map<String, String> sorts, String search) {
        Uni<TotalRowsAndData<CarEntity>> result = CarRepository.findAll(client, filters, search, sorts, 0, 0);
        return result.onItem().transform(totalRowsAndData -> {
            List<CarEntity> carList = totalRowsAndData.getData();
            return convertToXml(carList);
        }).await().indefinitely();
    }

    /**
     * Fills missing data for cars by fetching car information from an external AI
     * service and updating the database.
     *
     * @param client the PgPool client used for database operations
     * @return a GenericResponse indicating the success of the operation
     *
     *         This method performs the following steps:
     *         1. Initializes filters and pagination parameters.
     *         2. Iterates through pages of car data.
     *         3. Fetches car data from the repository based on filters and
     *         pagination.
     *         4. Concatenates car data into a single string.
     *         5. Sends the concatenated data to an AI service to get additional car
     *         information.
     *         6. Parses the response from the AI service.
     *         7. Updates the car information in the database.
     *         8. Logs the results of the operations.
     *
     *         If any JSON processing errors occur, they are logged.
     */
    public GenericResponse<String> fillMissingData(PgPool client) {

        Map<String, String> filters = new HashMap<>();
        filters.put("velocity", "0");

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

            if (concatenatedData.isEmpty()) {
                Log.info("No missing data found");
                continue;
            }

            String response = aiService.getCarInformation(concatenatedData);

            Log.info(response);

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                CarResponse carEntities = objectMapper.readValue(response, new TypeReference<CarResponse>() {
                });
                for (CarEntity car : carEntities.getCars()) {
                    // Log.info(objectMapper.writeValueAsString(car.getExteriorColors()));
                    // Log.info(car.toString());

                    CarRepository.updateCar(client, car.id(), car.color(), car.velocity())
                            .onItem().invoke(updateResult -> {
                                if (updateResult != null) {
                                    Log.info("Car with id " + updateResult.id() + " updated successfully");
                                }
                            }).await().indefinitely();
                }
            } catch (JsonProcessingException e) {
                // throw new RuntimeException("Error processing JSON", e);
                Log.error("Error processing JSON", e);
            }

        }

        return new GenericResponse<>("Missing data filled successfully",
                "Missing data filled successfully");
    }

    /**
     * Fetches UI parameters from the database using the provided PgPool client.
     * The parameters include makers, makers and models, transmissions, colors, fuel
     * types, and body types.
     * 
     * @param client the PgPool client used to interact with the database
     * @return a Uni containing a GenericResponse with a map of UI parameters and a
     *         success message
     */
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
                .with(results -> {
                    Map<String, Object> response = new HashMap<>();
                    int i = 0;
                    for (String key : uniMap.keySet()) {
                        response.put(key, results.get(i++)); // Map results to corresponding keys
                    }
                    return new GenericResponse<>(response, "UI parameters fetched successfully");
                });
    }

    /**
     * Fetches the image URLs for a car based on its ID.
     *
     * This method retrieves a car entity from the database using the provided
     * client and ID.
     * If the car is found, it checks for existing image URLs. If URLs are present,
     * it returns them.
     * If no URLs are found, it generates image URLs based on the car's details and
     * updates the database.
     *
     * @param client the database client used to fetch the car entity
     * @param id     the ID of the car to fetch the image URLs for
     * @return a Uni containing a GenericResponse with a map of color to image URL
     *         and a status message
     */
    public Uni<GenericResponse<Map<String, String>>> getImage(PgPool client, Long id) {
        Uni<CarEntity> carUni = CarRepository.getById(client, id);
        return carUni.onItem().transformToUni(car -> {
            if (car == null) {
                return Uni.createFrom().item(new GenericResponse<>(null, "Car not found"));
            }
            List<String> colors = getColors(car);
            List<String> urls = car.imageUrl();

            if (urls != null && !urls.isEmpty()) {
                return Uni.createFrom().item(getResponseFromUrls(colors, urls));
            }

            return fetchAndStoreImageUrls(client, id, car, colors);
        });
    }

    private List<String> getColors(CarEntity car) {
        List<String> colors = car.color();
        if (colors == null || colors.isEmpty()) {
            colors = List.of("Black", "White", "Gray", "Silver", "Blue", "Red");
        }
        return colors;
    }

    private GenericResponse<Map<String, String>> getResponseFromUrls(List<String> colors, List<String> urls) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < colors.size(); i++) {
            result.put(colors.get(i), urls.get(i));
        }
        Log.info("Image fetched from database");
        return new GenericResponse<>(result, "Image fetched successfully");
    }

    private Uni<GenericResponse<Map<String, String>>> fetchAndStoreImageUrls(PgPool client, Long id, CarEntity car,
            List<String> colors) {
        List<Uni<Map.Entry<String, String>>> imageUnis = colors.stream()
                .map(color -> {
                    String keyword = "Car " + car.trimYear() + " " + car.make() + " " + car.model() + " "
                            + car.trimName() + " " + color;
                    return fetchImageUrl(keyword)
                            .onItem().transform(url -> Map.entry(color, url));
                })
                .toList();

        return Uni.combine().all().unis(imageUnis)
                .with(entries -> {
                    Map<String, String> result = new HashMap<>();
                    for (Object entry : entries) {
                        Map.Entry<String, String> mapEntry = (Map.Entry<String, String>) entry;
                        result.put(mapEntry.getKey(), mapEntry.getValue());
                    }

                    List<String> colorList = new ArrayList<>(result.keySet());
                    List<String> urlList = new ArrayList<>(result.values());
                    urlList.removeIf(String::isEmpty);

                    if (!urlList.isEmpty()) {
                        CarRepository.updateCarColorAndImageUrl(client, id, colorList, urlList);
                    }

                    return new GenericResponse<>(result, "Image fetched successfully");
                });
    }

    /**
     * Fetches the image URL based on the provided keyword.
     *
     * This method performs the following steps:
     * 1. Encodes the provided keyword.
     * 2. Fetches the search URL using the encoded keyword.
     * 3. Fetches the image URL from the search URL.
     *
     * @param keyword the keyword to search for an image.
     * @return a Uni containing the image URL as a String.
     */
    private Uni<String> fetchImageUrl(String keyword) {
        return Uni.createFrom().item(() -> encodeKeyword(keyword))
                .onItem().transformToUni(this::fetchSearchUrl)
                .onItem().transformToUni(this::fetchImageFromUrl);
    }

    /**
     * Encodes the given keyword for use in a URL and constructs a Google Image
     * search URL.
     *
     * @param keyword the keyword to be encoded and used in the search URL
     * @return the constructed Google Image search URL with the encoded keyword, or
     *         null if encoding fails
     */
    private String encodeKeyword(String keyword) {
        try {
            String encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8");
            String searchUrl = "https://www.google.com/search?q=" + encodedKeyword
                    + "&tbs=isz:l&biw=1528&bih=738&dpr=1.25&tbm=isch&hl=en";
            Log.info(searchUrl);
            return searchUrl;
        } catch (java.io.UnsupportedEncodingException e) {
            Log.error("Error encoding URL", e);
            return null;
        }
    }

    /**
     * Fetches the content of the given search URL asynchronously.
     *
     * @param searchUrl the URL to fetch content from. If null, a Uni containing a
     *                  null item is returned.
     * @return a Uni that emits the content of the URL as a String, or null if an
     *         error occurs.
     */
    private Uni<String> fetchSearchUrl(String searchUrl) {
        if (searchUrl == null) {
            return Uni.createFrom().nullItem();
        }

        String[] userAgents = {
                // "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like
                // Gecko) Chrome/58.0.3029.110 Safari/537.3",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
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
                        .thenApply(java.net.http.HttpResponse::body);
            } catch (Exception e) {
                Log.error("Error fetching search URL", e);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
    }

    /**
     * Fetches the URL of the first image from the given HTML response body.
     *
     * @param responseBody the HTML response body as a String
     * @return a Uni containing the URL of the first image if found, or an empty
     *         string if no image is found or if the response body is null
     */
    private Uni<String> fetchImageFromUrl(String responseBody) {
        if (responseBody == null) {
            return Uni.createFrom().nullItem();
        }

        Document doc = Jsoup.parse(responseBody);
        Element firstImage = doc.select("img[src^=https]").first();
        if (firstImage != null) {
            Log.info(firstImage.attr("src"));
        }

        String imageUrl = "";

        if (firstImage == null && useAzureBlob.equalsIgnoreCase("true")) {
            String regex = "'data:image/jpeg;base64,([^']+)";

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(responseBody);

            while (matcher.find()) {
                String base64 = matcher.group(1).replaceFirst("data:image/jpeg;base64,", "");
                String cleanedBase64 = base64.replaceAll("\\\\x[0-9a-fA-F]{2}", "");
                if (cleanedBase64.length() >= 5000) {
                    byte[] bytes = java.util.Base64.getDecoder().decode(cleanedBase64);
                    // try {
                    // java.io.File file = java.io.File.createTempFile("image", ".jpg");
                    // Log.info(file.toPath());
                    // Log.info(file.toURI().toString());
                    // java.nio.file.Files.write(file.toPath(), bytes);

                    Mono<String> blobUrl = blobServiceAsyncClient
                            .createBlobContainerIfNotExists("dev")
                            .map(it -> it.getBlobAsyncClient("images/" + java.util.UUID.randomUUID() + ".jpg"))
                            // .flatMap(it -> it.upload(BinaryData.fromBytes(bytes), true))
                            .flatMap(blobAsyncClient -> blobAsyncClient.upload(BinaryData.fromBytes(bytes), true)
                                    .map(blockBlobItem -> blobAsyncClient.getBlobUrl()));

                    imageUrl = Uni.createFrom().completionStage(blobUrl.toFuture()).await().indefinitely();

                    // } catch (java.io.IOException e) {
                    // Log.error("Error writing image to file", e);
                    // }
                    // only fetch one image per color to saving cost on storage
                    break;
                }
            }

            Log.info("Azure Image Url: " + imageUrl);
        }

        if (!imageUrl.isEmpty()) {
            return Uni.createFrom().item(imageUrl);
        }

        return Uni.createFrom().item(firstImage != null ? firstImage.attr("src") : "");
    }

    /**
     * Converts a list of CarEntity objects to an XML string representation.
     *
     * @param cars the list of CarEntity objects to be converted to XML
     * @return a string containing the XML representation of the list of cars
     */
    private String convertToXml(List<CarEntity> cars) {
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<cars>");
        for (CarEntity car : cars) {
            xmlBuilder.append("<car>");
            xmlBuilder.append("<id>").append(car.id()).append("</id>");
            xmlBuilder.append("<make>").append(car.make()).append("</make>");
            xmlBuilder.append("<model>").append(car.model()).append("</model>");
            xmlBuilder.append("<year>").append(car.trimYear()).append("</year>");
            xmlBuilder.append("<trimName>").append(car.trimName()).append("</trimName>");
            xmlBuilder.append("<trimDescription>").append(car.trimDescription()).append("</trimDescription>");
            xmlBuilder.append("<bodyType>").append(car.bodyType()).append("</bodyType>");
            xmlBuilder.append("<fuelType>").append(car.fuelType()).append("</fuelType>");
            xmlBuilder.append("<transmission>").append(car.transmission()).append("</transmission>");
            xmlBuilder.append("<color>").append(car.color()).append("</color>");
            xmlBuilder.append("<length>").append(car.length()).append("</length>");
            xmlBuilder.append("<weight>").append(car.weight()).append("</weight>");
            xmlBuilder.append("<velocity>").append(car.velocity()).append("</velocity>");
            xmlBuilder.append("</car>");
        }
        xmlBuilder.append("</cars>");
        return xmlBuilder.toString();
    }
}
