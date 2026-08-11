package com.sucre.surena.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 50)
    private String nit;

    @Column(length = 50)
    private String cnss;

    @Column(length = 150)
    private String zona;

    @Column(length = 200)
    private String calle;

    @Column(length = 100)
    private String ciudad;

    @Column(length = 50)
    private String telefono;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
