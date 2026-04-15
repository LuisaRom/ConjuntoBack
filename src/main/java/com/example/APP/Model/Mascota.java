package com.example.APP.Model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String tipo;
    private String raza;
    private String fotoUrl;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
