package com.example.APP.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class AccesoVehicular {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String placaVehiculo;
    private String torre;
    private String apartamento;
    private String codigoQr;

    @ManyToOne
    @JoinColumn(name = "visitante_id")
    private Visitante visitante;

    @ManyToOne
    @JoinColumn(name = "autorizado_por")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Usuario autorizadoPor;

    private LocalDateTime horaAutorizada;
    private LocalDateTime horaEntrada;
    private LocalDateTime horaSalida;
}

