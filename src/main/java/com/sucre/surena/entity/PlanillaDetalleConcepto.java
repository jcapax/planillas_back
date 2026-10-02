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

    @Column(name = "planilla_detalle_id", nullable = false)
    private Long planillaDetalleId;

    @Column(name = "planilla_id", nullable = false)
    private Long planillaId;

    @Column(name = "empleado_id", nullable = false)
    private Long empleadoId;

    @Column
    private Integer item;

    @Column(name = "horas_trabajadas")
    private BigDecimal horasTrabajadas;

    @Column(name = "jornal_hora")
    private BigDecimal jornalHora;

    @Column(name = "dias_antiguedad")
    private Integer diasAntiguedad;

    @Column(name = "bono_antig_pct")
    private BigDecimal bonoAntigPct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concepto_id", nullable = false)
    private Concepto concepto;

    @Column(length = 20)
    private String tipo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto = BigDecimal.ZERO;
}
