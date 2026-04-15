package com.example.APP.DTO;

import com.example.APP.Model.Usuario;

public class UsuarioResponseDto {
    private Long id;
    private String nombre;
    private String documento;
    private String telefono;
    private String usuario;
    private Usuario.Rol rol;
    private String torre;
    private String apartamento;

    public static UsuarioResponseDto fromEntity(Usuario usuarioEntity) {
        UsuarioResponseDto dto = new UsuarioResponseDto();
        dto.setId(usuarioEntity.getId());
        dto.setNombre(usuarioEntity.getNombre());
        dto.setDocumento(usuarioEntity.getDocumento());
        dto.setTelefono(usuarioEntity.getTelefono());
        dto.setUsuario(usuarioEntity.getUsuario());
        dto.setRol(usuarioEntity.getRol());
        dto.setTorre(usuarioEntity.getTorre());
        dto.setApartamento(usuarioEntity.getApartamento());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public Usuario.Rol getRol() {
        return rol;
    }

    public void setRol(Usuario.Rol rol) {
        this.rol = rol;
    }

    public String getTorre() {
        return torre;
    }

    public void setTorre(String torre) {
        this.torre = torre;
    }

    public String getApartamento() {
        return apartamento;
    }

    public void setApartamento(String apartamento) {
        this.apartamento = apartamento;
    }
}
