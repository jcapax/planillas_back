package com.sucre.surena.repository;

import com.sucre.surena.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    List<Empresa> findByActivoTrue();
}
