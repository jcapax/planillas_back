package com.sucre.surena.repository;

import com.sucre.surena.entity.Concepto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConceptoRepository extends JpaRepository<Concepto, Long> {
    Optional<Concepto> findByCodigo(String codigo);
    List<Concepto> findByActivoTrueOrderByOrdenAsc();
    List<Concepto> findByActivoTrueAndTipoOrderByOrdenAsc(String tipo);
    List<Concepto> findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(String tipo, String tipoDescuento);
}
