package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "parametro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parametro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(precision = 12, scale = 6)
    private BigDecimal valor;

    @Column(nullable = false, length = 30)
    private String unidad = "PORCENTAJE";

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;
}
