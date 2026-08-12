package com.sucre.surena.controller;

import com.sucre.surena.dto.EmpleadoDescuentoDTO;
import com.sucre.surena.dto.PapeletaDTO;
import com.sucre.surena.entity.Concepto;
import com.sucre.surena.entity.Empleado;
import com.sucre.surena.entity.Persona;
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
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional(readOnly = true)
    @GetMapping("/{id}/papeletas")
    public ResponseEntity<?> papeletas(@PathVariable Long id) {
        Planilla planilla = planillaRepository.findById(id).orElse(null);
        if (planilla == null) {
            return ResponseEntity.notFound().build();
        }
        List<PlanillaDetalle> detalles = detalleRepository.findByPlanillaIdOrderByItemAsc(id);
        List<Long> detalleIds = detalles.stream().map(PlanillaDetalle::getId).toList();

        Map<Long, List<PlanillaDetalleConcepto>> conceptosPorDetalle = detalleIds.isEmpty()
                ? Map.of()
                : detalleConceptoRepository.findByPlanillaDetalleIdInOrderByIdAsc(detalleIds).stream()
                        .collect(Collectors.groupingBy(pdc -> pdc.getPlanillaDetalle().getId()));

        List<PapeletaDTO> papeletas = detalles.stream().map(d -> {
            Empleado empleado = d.getEmpleado();
            Persona persona = empleado != null ? empleado.getPersona() : null;
            List<PapeletaDTO.Linea> lineas = conceptosPorDetalle.getOrDefault(d.getId(), List.of())
                    .stream()
                    .map(pdc -> new PapeletaDTO.Linea(
                            pdc.getConcepto().getCodigo(),
                            pdc.getConcepto().getNombre(),
                            pdc.getTipo(),
                            pdc.getMonto()))
                    .toList();
            return new PapeletaDTO(
                    d.getId(),
                    d.getItem(),
                    persona != null ? nombreCompleto(persona) : null,
                    persona != null ? persona.getTipoDocumento() + " " + persona.getNroDocumento() : null,
                    d.getTotalGanado(),
                    d.getTotalDescuentos(),
                    d.getLiquidoPagable(),
                    lineas);
        }).toList();

        Map<String, Object> body = new HashMap<>();
        body.put("planilla", planilla);
        body.put("papeletas", papeletas);
        return ResponseEntity.ok(body);
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

    private String nombreCompleto(Persona persona) {
        StringBuilder sb = new StringBuilder();
        if (persona.getNombres() != null && !persona.getNombres().isBlank()) sb.append(persona.getNombres().trim()).append(' ');
        if (persona.getApellidoPaterno() != null && !persona.getApellidoPaterno().isBlank()) sb.append(persona.getApellidoPaterno().trim()).append(' ');
        if (persona.getApellidoMaterno() != null && !persona.getApellidoMaterno().isBlank()) sb.append(persona.getApellidoMaterno().trim()).append(' ');
        if (persona.getApellidoCasada() != null && !persona.getApellidoCasada().isBlank()) sb.append(persona.getApellidoCasada().trim()).append(' ');
        return sb.toString().trim();
    }
}
