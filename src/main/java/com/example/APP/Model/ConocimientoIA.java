package com.example.APP.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conocimiento_ia")
@Getter
@Setter
public class ConocimientoIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String contenido;

    @Column(length = 60)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Estado estado;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Origen origen;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaActualizacion;

    private Integer usos = 0;

    @Transient
    public String getCreadoPorNombre() {
        return creadoPor != null ? creadoPor.getNombre() : null;
    }

    public enum Estado {
        PENDIENTE, APROBADO, RECHAZADO
    }

    public enum Origen {
        SISTEMA, ADMINISTRADOR, CONVERSACION, FEEDBACK
    }
}
