package com.sucre.surena.controller;

import com.sucre.surena.entity.Concepto;
import com.sucre.surena.repository.ConceptoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personal/conceptos")
@RequiredArgsConstructor
public class ConceptoController {

    private final ConceptoRepository conceptoRepository;

    @GetMapping
    public List<Concepto> listar(@RequestParam(required = false) String tipo) {
        if (tipo != null && !tipo.isBlank()) {
            return conceptoRepository.findByActivoTrueAndTipoOrderByOrdenAsc(tipo.toUpperCase());
        }
        return conceptoRepository.findByActivoTrueOrderByOrdenAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return conceptoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody Concepto concepto) {
        if (conceptoRepository.findByCodigo(concepto.getCodigo()).isPresent()) {
            return ResponseEntity.badRequest().body(error("Ya existe el concepto: " + concepto.getCodigo()));
        }
        concepto.setId(null);
        concepto.setActivo(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(conceptoRepository.save(concepto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody Concepto datos) {
        return conceptoRepository.findById(id)
                .map(existente -> {
                    datos.setId(existente.getId());
                    return ResponseEntity.ok(conceptoRepository.save(datos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return conceptoRepository.findById(id)
                .map(concepto -> {
                    concepto.setActivo(false);
                    conceptoRepository.save(concepto);
                    return ResponseEntity.ok(Map.of("mensaje", "Concepto eliminado"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
