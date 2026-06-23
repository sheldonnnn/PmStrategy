package com.cmbc.strategy.domain.dto.baseRes;

import java.util.List;

public class QueryOderBaseEnumResponse<T> {
    private List<T> data;

    private boolean success;
    private boolean error;
    private String code;
    private String message;


    private int total;//总条数

    public QueryOderBaseEnumResponse(List<T> data, String messgae) {
        this.data = data;
        this.success = true;
        this.message = messgae;
        this.error = true;
        this.code = "0000";
    }

    public QueryOderBaseEnumResponse(String messgae) {
        this.success = true;
        this.error = false;
        this.message = messgae;
        this.code = "9999";
    }

    public List<T> getData() { return data; }

    public void setData(List<T> data) { this.data = data; }

    public int getTotal() { return total; }

    public void setTotal(int total) { this.total = total; }

    public boolean isSuccess() { return success; }

    public void setSuccess(boolean success) { this.success = success; }

    public boolean isError() { return error; }

    public void setError(boolean error) { this.error = error; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public String getCode() { return code; }

    public void setCode(String code) { this.code = code; }
}
