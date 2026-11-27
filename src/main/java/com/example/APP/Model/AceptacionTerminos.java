package com.example.APP.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "aceptacion_terminos")
@Getter
@Setter
public class AceptacionTerminos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 20)
    private String version;

    @Column(length = 20)
    private String rol;

    private Boolean aceptaTerminos;

    private Boolean autorizaTratamientoDatos;

    private Boolean aceptaAvisoIA;

    private LocalDateTime fechaAceptacion;

    @Column(length = 200)
    private String dispositivo;

    @Transient
    public String getUsuarioNombre() {
        return usuario != null ? usuario.getNombre() : null;
    }

    @Transient
    public String getUsuarioLogin() {
        return usuario != null ? usuario.getUsuario() : null;
    }
}
