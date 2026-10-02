package com.sucre.surena.repository;

import com.sucre.surena.entity.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
    Optional<Configuracion> findFirstByOrderByIdAsc();
}
