package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "empleado",
        uniqueConstraints = @UniqueConstraint(name = "uq_empleado_documento",
                columnNames = {"tipo_documento", "nro_documento"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_documento", nullable = false, length = 30)
    private String tipoDocumento = "CI";

    @Column(name = "nro_documento", nullable = false, length = 50)
    private String nroDocumento;

    @Column(name = "apellido_paterno", length = 100)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 100)
    private String apellidoMaterno;

    @Column(name = "apellido_casada", length = 100)
    private String apellidoCasada;

    @Column(length = 100)
    private String nombre1;

    @Column(name = "otros_nombres", length = 100)
    private String otrosNombres;

    @Column(length = 1)
    private String sexo;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "pais_nacionalidad", nullable = false, length = 60)
    private String paisNacionalidad = "Bolivia";

    @Column(length = 60)
    private String afp;

    @Column(name = "nua_cua", length = 50)
    private String nuaCua;

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

    @Column(length = 255)
    private String direccion;

    @Column(length = 50)
    private String telefono;

    @Column(name = "jornal_hora", nullable = false, precision = 12, scale = 4)
    private java.math.BigDecimal jornalHora = java.math.BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Transient
    public String getNombresCompletos() {
        StringBuilder sb = new StringBuilder();
        if (nombre1 != null && !nombre1.isBlank()) sb.append(nombre1.trim()).append(' ');
        if (otrosNombres != null && !otrosNombres.isBlank()) sb.append(otrosNombres.trim()).append(' ');
        if (apellidoPaterno != null && !apellidoPaterno.isBlank()) sb.append(apellidoPaterno.trim()).append(' ');
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) sb.append(apellidoMaterno.trim()).append(' ');
        if (apellidoCasada != null && !apellidoCasada.isBlank()) sb.append(apellidoCasada.trim()).append(' ');
        return sb.toString().trim();
    }
}
