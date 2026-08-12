package com.sucre.surena.repository;

import com.sucre.surena.entity.EmpleadoDescuento;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmpleadoDescuentoRepository extends JpaRepository<EmpleadoDescuento, Long> {

    List<EmpleadoDescuento> findByEmpleadoIdOrderByConceptoOrdenAsc(Long empleadoId);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmpleadoDescuento d WHERE d.empleado.id = :empleadoId")
    void deleteByEmpleadoId(@Param("empleadoId") Long empleadoId);
}
