package com.example.APP.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "mensaje_ia")
@Getter
@Setter
public class MensajeIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "conversacion_id")
    private ConversacionIA conversacion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Rol rol;

    @Column(columnDefinition = "TEXT")
    private String contenido;

    @Column(length = 500)
    private String herramientasUsadas;

    private Boolean util;

    private LocalDateTime fecha;

    public enum Rol {
        USUARIO, ASISTENTE
    }
}
