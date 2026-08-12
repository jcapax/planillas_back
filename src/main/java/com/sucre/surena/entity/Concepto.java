package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "concepto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concepto {

    public static final String TIPO_HABER = "HABER";
    public static final String TIPO_DESCUENTO = "DESCUENTO";
    public static final String TIPO_APORTE = "APORTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "aplica_porcentaje", nullable = false)
    private Boolean aplicaPorcentaje = false;

    @Column(precision = 8, scale = 4)
    private BigDecimal porcentaje;

    @Column(name = "tipo_descuento", length = 20)
    private String tipoDescuento;

    @Column(precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private Boolean activo = true;
}
