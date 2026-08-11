package com.sucre.surena.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "planilla")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Planilla {

    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "periodo_anio", nullable = false)
    private Integer periodoAnio;

    @Column(name = "periodo_mes", nullable = false)
    private Integer periodoMes;

    @Column(length = 150)
    private String nombre;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(nullable = false, length = 30)
    private String estado = ESTADO_BORRADOR;

    @Column(name = "fecha_liquidacion")
    private LocalDate fechaLiquidacion;

    @Column(name = "total_haberes", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalHaberes = BigDecimal.ZERO;

    @Column(name = "total_descuentos", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDescuentos = BigDecimal.ZERO;

    @Column(name = "total_liquido", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalLiquido = BigDecimal.ZERO;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
