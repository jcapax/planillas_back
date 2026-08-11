package com.sucre.surena.repository;

import com.sucre.surena.entity.Parametro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParametroRepository extends JpaRepository<Parametro, Long> {
    Optional<Parametro> findByCodigo(String codigo);
    List<Parametro> findByActivoTrueOrderByCodigoAsc();
}
