package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bono_antiguedad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BonoAntiguedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "desde_anios")
    private Integer desdeAnios;

    @Column(name = "hasta_anios")
    private Integer hastaAnios;

    @Column(name = "desde_dias")
    private Integer desdeDias;

    @Column(name = "hasta_dias")
    private Integer hastaDias;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal porcentaje;

    @Column(length = 150)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;
}
