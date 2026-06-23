package com.cmbc.strategy.domain.dto.baseRes;

/**
 * 查询单个实体的返回
 * @param <T>
 */
public class QueryVoBaseResponse<T>{
    private T data;

    private boolean success;
    private boolean error;
    private String code;
    private String message;


    public QueryVoBaseResponse(T inv) {
        this.data = inv;
        this.success = true;
        this.error = true;
        this.message = "success";
        this.code = "0000";
    }

    public QueryVoBaseResponse(T inv, String messgae) {
        this.data = inv;
        this.success = true;
        this.error = false;
        this.code = "9999";
        this.message = messgae;
    }

    public QueryVoBaseResponse(T inv, String code, String messgae) {
        this.data = inv;
        this.success = true;
        this.message = messgae;
        this.code = code;
        if("0000".equals(code)){
            this.error = true;
        }else{
            this.error = false;
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
