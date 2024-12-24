package com.pstag.utils;

import java.util.List;

public class TotalRowsAndData<T> {
    private final int totalRows;
    private final List<T> data;

    public TotalRowsAndData(int totalRows, List<T> data) {
        this.totalRows = totalRows;
        this.data = data;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public List<T> getData() {
        return data;
    }
}
