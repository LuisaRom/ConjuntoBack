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
}
