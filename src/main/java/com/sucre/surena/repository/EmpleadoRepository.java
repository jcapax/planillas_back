package com.sucre.surena.repository;

import com.sucre.surena.entity.Empleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    List<Empleado> findByActivoTrue(Sort sort);

    Page<Empleado> findByActivoTrue(Pageable pageable);

    @Query("SELECT COUNT(e) > 0 FROM Empleado e WHERE e.persona.id = :personaId")
    boolean existsByPersonaId(@Param("personaId") Long personaId);

    @Query("SELECT e FROM Empleado e JOIN e.persona p WHERE e.activo = true AND (" +
            "LOWER(p.apellidoPaterno) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.apellidoMaterno) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.nombres) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "p.nroDocumento LIKE CONCAT('%', :q, '%'))")
    Page<Empleado> buscar(@Param("q") String q, Pageable pageable);
}
