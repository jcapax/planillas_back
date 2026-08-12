package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "persona",
        uniqueConstraints = @UniqueConstraint(name = "uq_persona_documento",
                columnNames = {"tipo_documento", "nro_documento"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Persona {

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

    @Column(length = 200)
    private String nombres;

    @Column(length = 1)
    private String sexo;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "pais_nacionalidad", nullable = false, length = 60)
    private String paisNacionalidad = "Bolivia";

    @Column(length = 255)
    private String direccion;

    @Column(length = 50)
    private String telefono;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
