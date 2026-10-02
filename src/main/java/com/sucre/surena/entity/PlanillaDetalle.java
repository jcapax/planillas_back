package com.sucre.surena.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "v_planilla_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanillaDetalle {

    @Id
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planilla_id", nullable = false)
    private Planilla planilla;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column
    private Integer item;

    @Column(name = "horas_trabajadas", nullable = false, precision = 8, scale = 2)
    private BigDecimal horasTrabajadas = BigDecimal.ZERO;

    @Column(name = "jornal_hora", nullable = false, precision = 12, scale = 4)
    private BigDecimal jornalHora = BigDecimal.ZERO;

    @Column(name = "haber_basico", nullable = false, precision = 14, scale = 2)
    private BigDecimal haberBasico = BigDecimal.ZERO;

    @Column(name = "dias_antiguedad", nullable = false)
    private Integer diasAntiguedad = 0;

    @Column(name = "bono_antig_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal bonoAntigPct = BigDecimal.ZERO;

    @Column(name = "salario_dominical", nullable = false, precision = 14, scale = 2)
    private BigDecimal salarioDominical = BigDecimal.ZERO;

    @Column(name = "bono_antig_monto", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonoAntigMonto = BigDecimal.ZERO;

    @Column(name = "total_ganado", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGanado = BigDecimal.ZERO;

    @Column(name = "aporte_solidario", nullable = false, precision = 14, scale = 2)
    private BigDecimal aporteSolidario = BigDecimal.ZERO;

    @Column(name = "aporte_nacional", nullable = false, precision = 14, scale = 2)
    private BigDecimal aporteNacional = BigDecimal.ZERO;

    @Column(name = "aporte_afp", nullable = false, precision = 14, scale = 2)
    private BigDecimal aporteAfp = BigDecimal.ZERO;

    @Column(name = "aporte_riesgo_comun", nullable = false, precision = 14, scale = 2)
    private BigDecimal aporteRiesgoComun = BigDecimal.ZERO;

    @Column(name = "total_aportes", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAportes = BigDecimal.ZERO;

    @Column(name = "descuentos_varios", nullable = false, precision = 14, scale = 2)
    private BigDecimal descuentosVarios = BigDecimal.ZERO;

    @Column(name = "total_descuentos", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDescuentos = BigDecimal.ZERO;

    @Column(name = "liquido_pagable", nullable = false, precision = 14, scale = 2)
    private BigDecimal liquidoPagable = BigDecimal.ZERO;
}
