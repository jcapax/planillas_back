package com.sucre.surena.controller;

import com.sucre.surena.dto.EmpleadoDescuentoDTO;
import com.sucre.surena.entity.Concepto;
import com.sucre.surena.entity.Planilla;
import com.sucre.surena.entity.PlanillaDetalle;
import com.sucre.surena.entity.PlanillaDetalleConcepto;
import com.sucre.surena.repository.ConceptoRepository;
import com.sucre.surena.repository.PlanillaDetalleConceptoRepository;
import com.sucre.surena.repository.PlanillaDetalleRepository;
import com.sucre.surena.repository.PlanillaRepository;
import com.sucre.surena.service.PlanillaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaRepository planillaRepository;
    private final PlanillaDetalleRepository detalleRepository;
    private final PlanillaDetalleConceptoRepository detalleConceptoRepository;
    private final ConceptoRepository conceptoRepository;
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

    @GetMapping("/detalles/{detalleId}/descuentos")
    public ResponseEntity<?> descuentos(@PathVariable Long detalleId) {
        if (!detalleRepository.existsById(detalleId)) {
            return ResponseEntity.notFound().build();
        }
        List<Concepto> variables = conceptoRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "VARIABLE");
        Map<Long, BigDecimal> montos = detalleConceptoRepository
                .findByPlanillaDetalleIdOrderByIdAsc(detalleId).stream()
                .filter(pdc -> pdc.getTipo() != null && Concepto.TIPO_DESCUENTO.equals(pdc.getTipo()))
                .collect(Collectors.toMap(pdc -> pdc.getConcepto().getId(), PlanillaDetalleConcepto::getMonto));
        List<EmpleadoDescuentoDTO> result = variables.stream()
                .map(c -> new EmpleadoDescuentoDTO(c.getId(), c.getCodigo(), c.getNombre(),
                        montos.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/detalles/{detalleId}/descuentos")
    public ResponseEntity<?> guardarDescuentos(@PathVariable Long detalleId,
                                               @Valid @RequestBody List<EmpleadoDescuentoDTO> descuentos) {
        try {
            return ResponseEntity.ok(planillaService.actualizarDescuentos(detalleId, descuentos));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
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
