package com.pstag.utils;

public class GenericResponse<T> {
    private final T data;
    private final String message;

    public GenericResponse(T data, String message) {
        this.data = data;
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

}
