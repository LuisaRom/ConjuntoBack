package com.example.APP.Model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class VehiculoResidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String placa;
    private String marca;
    private String modelo;
    private String color;

    @Column(length = 20)
    private String tipo;

    @Column(length = 30)
    private String parqueadero;

    private LocalDateTime fechaRegistro;

    private LocalDateTime fechaAsignacion;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
