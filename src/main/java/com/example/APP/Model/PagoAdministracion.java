package com.example.APP.Model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class PagoAdministracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal monto;
    private LocalDateTime fechaPago;

    @Enumerated(EnumType.STRING)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    private EstadoPago estadoPago;

    private String concepto;
    private String periodo;
    private String referenciaExterna;

    @Column(length = 80)
    private String mercadoPagoPreferenceId;

    @Column(length = 80)
    private String mercadoPagoPaymentId;

    @Column(length = 512)
    private String checkoutUrl;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public enum MetodoPago {
        EN_LINEA, EFECTIVO
    }

    public enum EstadoPago {
        PENDIENTE, APROBADO, RECHAZADO
    }
}
