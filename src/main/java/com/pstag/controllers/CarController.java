package com.pstag.controllers;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import com.pstag.entities.CarEntity;
import com.pstag.services.CarService;
import com.pstag.utils.GenericResponse;
import com.pstag.utils.TotalRowsAndData;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;

import jakarta.inject.Inject;

@Path("/api/cars")
public class CarController {
    private final PgPool client;

    private final CarService service;

    @Inject
    public CarController(PgPool client, CarService service) {
        this.client = client;
        this.service = service;
    }

    @GET
    public Uni<TotalRowsAndData<CarEntity>> get(
            @Context UriInfo uriInfo,
            @QueryParam("search") String search,
            @QueryParam("limit") @DefaultValue("10") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        Map<String, String> filters = uriInfo.getQueryParameters().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("filter["))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(7, entry.getKey().length() - 1),
                        entry -> entry.getValue().get(0)));
        Map<String, String> sorts = uriInfo.getQueryParameters().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("sort["))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(5, entry.getKey().length() - 1),
                        entry -> entry.getValue().get(0)));

        return service.findAll(client, filters, search, sorts, limit, offset);
    }

    @GET
    @Path("/xml")
    @Produces("application/xml")
    public Response getXml(@Context UriInfo uriInfo,
            @QueryParam("search") String search) {
        Map<String, String> filters = uriInfo.getQueryParameters().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("filter["))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(7, entry.getKey().length() - 1),
                        entry -> entry.getValue().get(0)));
        Map<String, String> sorts = uriInfo.getQueryParameters().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("sort["))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().substring(5, entry.getKey().length() - 1),
                        entry -> entry.getValue().get(0)));
        String xml = service.getXml(client, filters, sorts, search);
        return Response.ok(xml).header("Content-Disposition", "attachment; filename=\"cars.xml\"").build();
    }

    @PATCH
    @Path("/fill-missing-data")
    public GenericResponse<String> fillMissingData() {
        return service.fillMissingData(client);
    }
}
