package com.sucre.surena.repository;

import com.sucre.surena.entity.Planilla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanillaRepository extends JpaRepository<Planilla, Long> {
    Optional<Planilla> findByPeriodoAnioAndPeriodoMes(Integer periodoAnio, Integer periodoMes);
    List<Planilla> findByOrderByPeriodoAnioDescPeriodoMesDesc();
}
