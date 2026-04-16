package com.example.APP.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class HistorialNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long notificacionOriginalId;
    private String mensaje;
    private LocalDateTime fechaEnvio;
    private String imagenUrl;
    private String videoUrl;
    private String usuariosEtiquetados;
    private Long usuarioId;
    private LocalDateTime fechaArchivado;

    // Getters/Setters manuales para evitar errores de resolución en IDE
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNotificacionOriginalId() {
        return notificacionOriginalId;
    }

    public void setNotificacionOriginalId(Long notificacionOriginalId) {
        this.notificacionOriginalId = notificacionOriginalId;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public void setFechaEnvio(LocalDateTime fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getUsuariosEtiquetados() {
        return usuariosEtiquetados;
    }

    public void setUsuariosEtiquetados(String usuariosEtiquetados) {
        this.usuariosEtiquetados = usuariosEtiquetados;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalDateTime getFechaArchivado() {
        return fechaArchivado;
    }

    public void setFechaArchivado(LocalDateTime fechaArchivado) {
        this.fechaArchivado = fechaArchivado;
    }
}
