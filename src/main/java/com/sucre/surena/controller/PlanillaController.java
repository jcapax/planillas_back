package com.sucre.surena.controller;

import com.sucre.surena.entity.Planilla;
import com.sucre.surena.entity.PlanillaDetalle;
import com.sucre.surena.entity.PlanillaDetalleConcepto;
import com.sucre.surena.repository.PlanillaDetalleConceptoRepository;
import com.sucre.surena.repository.PlanillaDetalleRepository;
import com.sucre.surena.repository.PlanillaRepository;
import com.sucre.surena.service.PlanillaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaRepository planillaRepository;
    private final PlanillaDetalleRepository detalleRepository;
    private final PlanillaDetalleConceptoRepository detalleConceptoRepository;
    private final PlanillaService planillaService;

    @GetMapping
    public List<Planilla> listar() {
        return planillaRepository.findByOrderByPeriodoAnioDescPeriodoMesDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return planillaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestParam Integer anio, @RequestParam Integer mes) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(planillaService.crearPlanilla(anio, mes));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/generar")
    public ResponseEntity<?> generar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(planillaService.generar(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @GetMapping("/{id}/detalles")
    public List<PlanillaDetalle> detalles(@PathVariable Long id) {
        return detalleRepository.findByPlanillaIdOrderByItemAsc(id);
    }

    @GetMapping("/detalles/{detalleId}/conceptos")
    public List<PlanillaDetalleConcepto> conceptos(@PathVariable Long detalleId) {
        return detalleConceptoRepository.findByPlanillaDetalleIdOrderByIdAsc(detalleId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return planillaRepository.findById(id)
                .map(planilla -> {
                    planillaService.eliminar(id);
                    return ResponseEntity.ok(Map.of("mensaje", "Planilla eliminada"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
