package com.sucre.surena.controller;

import com.sucre.surena.entity.BonoAntiguedad;
import com.sucre.surena.repository.BonoAntiguedadRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personal/bonos-antiguedad")
@RequiredArgsConstructor
public class BonoAntiguedadController {

    private final BonoAntiguedadRepository bonoAntiguedadRepository;

    @GetMapping
    public List<BonoAntiguedad> listar() {
        return bonoAntiguedadRepository.findByActivoTrueOrderByDesdeDiasAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return bonoAntiguedadRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody BonoAntiguedad bono) {
        if (bono.getDesdeDias() != null && bonoAntiguedadRepository.existsByDesdeDias(bono.getDesdeDias())) {
            return ResponseEntity.badRequest().body(error(
                    "Ya existe un rango con 'Desde (días)' = " + bono.getDesdeDias()));
        }
        bono.setId(null);
        bono.setActivo(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(bonoAntiguedadRepository.save(bono));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody BonoAntiguedad datos) {
        return bonoAntiguedadRepository.findById(id)
                .map(existente -> {
                    if (datos.getDesdeDias() != null && bonoAntiguedadRepository
                            .existsByDesdeDiasAndIdNot(datos.getDesdeDias(), id)) {
                        return ResponseEntity.badRequest().body(error(
                                "Ya existe un rango con 'Desde (días)' = " + datos.getDesdeDias()));
                    }
                    datos.setId(existente.getId());
                    return ResponseEntity.ok(bonoAntiguedadRepository.save(datos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return bonoAntiguedadRepository.findById(id)
                .map(bono -> {
                    bono.setActivo(false);
                    bonoAntiguedadRepository.save(bono);
                    return ResponseEntity.ok(Map.of("mensaje", "Registro eliminado"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
