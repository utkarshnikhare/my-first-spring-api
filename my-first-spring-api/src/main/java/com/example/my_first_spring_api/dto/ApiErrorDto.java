package com.example.my_first_spring_api.dto;

public class ApiErrorDto {
    private String error;
    private String message;
    private int status;

    public ApiErrorDto() {}

    public ApiErrorDto(String error, String message, int status) {
        this.error = error;
        this.message = message;
        this.status = status;
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
}
