package com.sucre.surena.repository;

import com.sucre.surena.entity.Persona;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    Optional<Persona> findByTipoDocumentoAndNroDocumento(String tipoDocumento, String nroDocumento);

    boolean existsByTipoDocumentoAndNroDocumento(String tipoDocumento, String nroDocumento);

    @Query("SELECT p FROM Persona p WHERE " +
            "LOWER(p.apellidoPaterno) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.apellidoMaterno) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.nombres) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "p.nroDocumento LIKE CONCAT('%', :q, '%')")
    Page<Persona> buscar(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Persona p WHERE NOT EXISTS (SELECT e FROM Empleado e WHERE e.persona = p)")
    List<Persona> findDisponibles();
}
