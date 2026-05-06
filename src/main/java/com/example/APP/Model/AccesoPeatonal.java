package com.example.APP.Model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class AccesoPeatonal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonAlias({"nombre", "visitante", "nombre_visitante"})
    private String nombreVisitante;
    private String torre;
    private String apartamento;
    private String codigoQr;

    @ManyToOne
    @JoinColumn(name = "visitante_id")
    private Visitante visitante;

    @ManyToOne
    @JoinColumn(name = "autorizado_por")
    private Usuario autorizadoPor;

    private LocalDateTime horaAutorizada;
    private LocalDateTime horaEntrada;
    private LocalDateTime horaSalida;
    @JsonAlias({"fechaAcceso", "fecha_acceso"})
    private LocalDate fecha;
}
