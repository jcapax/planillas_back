package com.sucre.surena.repository;

import com.sucre.surena.entity.Empleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    Optional<Empleado> findByTipoDocumentoAndNroDocumento(String tipoDocumento, String nroDocumento);

    boolean existsByTipoDocumentoAndNroDocumento(String tipoDocumento, String nroDocumento);

    List<Empleado> findByActivoTrueOrderByApellidoPaternoAsc();

    Page<Empleado> findByActivoTrue(Pageable pageable);

    @Query("SELECT e FROM Empleado e WHERE e.activo = true AND (" +
            "LOWER(e.apellidoPaterno) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.apellidoMaterno) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.nombre1) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.nroDocumento LIKE CONCAT('%', :q, '%'))")
    Page<Empleado> buscar(@Param("q") String q, Pageable pageable);
}
