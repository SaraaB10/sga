package co.edu.uniquindio.sga.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un pago del folio con su medio y su fecha (RN-15), fila de la tabla hija folio_pago. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PagoEmbedable {

    @Column(nullable = false)
    private BigDecimal valor;

    /** Código del medio (MedioPago): el mismo texto que guardaba el enum, así las bases existentes siguen sirviendo */
    @Column(nullable = false)
    private String medio;

    @Column(nullable = false)
    private LocalDate fecha;
}