package com.example.APP.DTO;

public class ErrorResponseDto {
    private String error;
    private String mensaje;

    public ErrorResponseDto() {
    }

    public ErrorResponseDto(String error) {
        this.error = error;
        this.mensaje = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMensaje() {
        return mensaje != null ? mensaje : error;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
