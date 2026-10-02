package com.sucre.surena.repository;

import com.sucre.surena.entity.PlanillaDetalleConcepto;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlanillaDetalleConceptoRepository extends JpaRepository<PlanillaDetalleConcepto, Long> {
    List<PlanillaDetalleConcepto> findByPlanillaDetalleIdOrderByIdAsc(Long planillaDetalleId);

    List<PlanillaDetalleConcepto> findByPlanillaDetalleIdInOrderByIdAsc(Collection<Long> planillaDetalleIds);
    void deleteByPlanillaDetalleId(Long planillaDetalleId);
    void deleteByPlanillaId(Long planillaId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlanillaDetalleConcepto pdc WHERE pdc.planillaDetalleId = :detalleId AND pdc.concepto.id IN :conceptoIds")
    void deleteByPlanillaDetalleIdAndConceptoIdIn(@Param("detalleId") Long detalleId,
                                                  @Param("conceptoIds") Collection<Long> conceptoIds);
}
