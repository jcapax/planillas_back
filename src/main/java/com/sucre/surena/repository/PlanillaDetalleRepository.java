package com.sucre.surena.repository;

import com.sucre.surena.entity.PlanillaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanillaDetalleRepository extends JpaRepository<PlanillaDetalle, Long> {
    List<PlanillaDetalle> findByPlanillaIdOrderByItemAsc(Long planillaId);
    void deleteByPlanillaId(Long planillaId);
}
