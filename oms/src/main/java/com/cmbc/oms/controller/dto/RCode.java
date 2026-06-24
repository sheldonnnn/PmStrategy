package com.cmbc.oms.controller.dto;

import java.io.Serializable;

public class RCode implements Serializable {
    private String code;
    private String domain;
    private String message;
    private String type;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public String toString() {
        return "RCode{" +
                "code='" + code + '\'' +
                ", domain='" + domain + '\'' +
                ", message='" + message + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
