package com.example.APP.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Setter
public class ReservaZonaComun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String zonaComun;
    private LocalDate fechaReserva;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    
    @Column(name = "servicios_adicionales", columnDefinition = "TEXT")
    private String serviciosAdicionales;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
