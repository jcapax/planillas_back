package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "planilla_detalle_concepto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanillaDetalleConcepto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planilla_detalle_id", nullable = false)
    private PlanillaDetalle planillaDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concepto_id", nullable = false)
    private Concepto concepto;

    @Column(length = 20)
    private String tipo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto = BigDecimal.ZERO;
}
