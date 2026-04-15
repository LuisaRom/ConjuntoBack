package com.example.APP.DTO;

public class LoginResponseDto {
    private String token;
    private String tokenType;
    private UsuarioResponseDto usuario;

    public LoginResponseDto() {
    }

    public LoginResponseDto(String token, String tokenType, UsuarioResponseDto usuario) {
        this.token = token;
        this.tokenType = tokenType;
        this.usuario = usuario;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public UsuarioResponseDto getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioResponseDto usuario) {
        this.usuario = usuario;
    }
}
