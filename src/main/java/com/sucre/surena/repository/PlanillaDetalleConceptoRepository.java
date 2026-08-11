package com.sucre.surena.repository;

import com.sucre.surena.entity.PlanillaDetalleConcepto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanillaDetalleConceptoRepository extends JpaRepository<PlanillaDetalleConcepto, Long> {
    List<PlanillaDetalleConcepto> findByPlanillaDetalleIdOrderByIdAsc(Long planillaDetalleId);
    void deleteByPlanillaDetalleId(Long planillaDetalleId);
    void deleteByPlanillaDetalle_PlanillaId(Long planillaId);
}
