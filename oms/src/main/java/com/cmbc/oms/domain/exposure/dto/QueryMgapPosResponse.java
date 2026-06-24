package com.cmbc.oms.domain.exposure.dto;

/**
 * 查询单个实体的返回
 * @param <T>
 */
public class QueryMgapPosResponse<T>{
    private T data;

    private boolean success;
    private boolean error;
    private String code;
    private String message;

    public QueryMgapPosResponse(T inv, boolean isConnected) {
        this.data = inv;
        this.success = isConnected;
        if(isConnected){
            this.error = false;
            this.message = "success";
            this.code = "0000";
        }else {
            this.error = true;
            this.code = "9999";
            this.message = "error";
        }
    }

    public boolean isSuccess() { return success; }

    public void setSuccess(boolean success) { this.success = success; }

    public boolean isError() { return error; }

    public void setError(boolean error) { this.error = error; }

    public String getCode() { return code; }

    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }

    public void setData(T data) { this.data = data; }
}
