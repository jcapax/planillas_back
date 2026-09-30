package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "empleado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "persona_id", nullable = false, unique = true)
    private Persona persona;

    @Transient
    private Long personaId;

    @Column(length = 60)
    private String afp;

    @Column(name = "nua_cua", length = 50)
    private String nuaCua;

    @Column(name = "cuenta_bancaria", length = 50)
    private String cuentaBancaria;

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(name = "fecha_seguro")
    private LocalDate fechaSeguro;

    @Column(length = 10)
    private String origen;

    @Column(length = 150)
    private String cargo;

    @Column(name = "clasificacion_laboral", length = 150)
    private String clasificacionLaboral;

    @Column(nullable = false)
    private Boolean jubilado = false;

    @Column(name = "jornal_hora", nullable = false, precision = 12, scale = 4)
    private BigDecimal jornalHora = BigDecimal.ZERO;

    @Column(name = "horas_trabajadas", nullable = false, precision = 8, scale = 2)
    private BigDecimal horasTrabajadas = new BigDecimal("208.00");

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Transient
    public String getNombresCompletos() {
        if (persona == null) return "";
        StringBuilder sb = new StringBuilder();
        if (persona.getNombres() != null && !persona.getNombres().isBlank()) sb.append(persona.getNombres().trim()).append(' ');
        if (persona.getApellidoPaterno() != null && !persona.getApellidoPaterno().isBlank()) sb.append(persona.getApellidoPaterno().trim()).append(' ');
        if (persona.getApellidoMaterno() != null && !persona.getApellidoMaterno().isBlank()) sb.append(persona.getApellidoMaterno().trim()).append(' ');
        if (persona.getApellidoCasada() != null && !persona.getApellidoCasada().isBlank()) sb.append(persona.getApellidoCasada().trim()).append(' ');
        return sb.toString().trim();
    }
}
