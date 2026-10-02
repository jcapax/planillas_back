package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minimo_nacional", nullable = false, precision = 14, scale = 2)
    private BigDecimal minimoNacional = BigDecimal.ZERO;

    @Column(name = "cantidad_minimo_nacional", nullable = false, precision = 14, scale = 2)
    private BigDecimal cantidadMinimoNacional = BigDecimal.ONE;

    @Column(name = "edad_riesgo_comun")
    private Integer edadRiesgoComun;

    @Column(name = "edad_riesgo_comun_pct", precision = 8, scale = 4)
    private BigDecimal edadRiesgoComunPct;
}
