package com.cmbc.strategy.configuration;

import lombok.Data;

@Data
public class BusinessException extends Exception {

    private String errorCode;
    private String errorMessage;
    private Object data;

    public BusinessException(String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

    public BusinessException(String errorCode, String errorMessage) {
        this(errorMessage);
        this.errorCode = errorCode;
    }
}
