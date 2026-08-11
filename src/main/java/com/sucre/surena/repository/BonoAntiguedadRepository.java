package com.sucre.surena.repository;

import com.sucre.surena.entity.BonoAntiguedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BonoAntiguedadRepository extends JpaRepository<BonoAntiguedad, Long> {

    List<BonoAntiguedad> findByActivoTrueOrderByDesdeDiasAsc();

    Optional<BonoAntiguedad> findByActivoTrueAndDesdeDiasLessThanEqualAndHastaDiasGreaterThanEqual(Integer dias, Integer dias2);

    Optional<BonoAntiguedad> findFirstByActivoTrueAndDesdeDiasLessThanEqualOrderByDesdeDiasDesc(Integer dias);
}
