package com.sucre.surena.controller;

import com.sucre.surena.dto.DescuentoCeldaDTO;
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
import com.sucre.surena.service.PlanillaPdfService;
import com.sucre.surena.service.PlanillaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/personal/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaRepository planillaRepository;
    private final PlanillaDetalleRepository detalleRepository;
    private final PlanillaDetalleConceptoRepository detalleConceptoRepository;
    private final ConceptoRepository conceptoRepository;
    private final PlanillaService planillaService;
    private final PlanillaPdfService planillaPdfService;

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

    @PostMapping("/{id}/recalcular")
    public ResponseEntity<?> recalcular(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(planillaService.recalcular(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @GetMapping("/{id}/detalles")
    public List<PlanillaDetalle> detalles(@PathVariable Long id) {
        return detalleRepository.findByPlanillaIdOrderByItemAsc(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> pdf(@PathVariable Long id) {
        Planilla planilla = planillaRepository.findById(id).orElse(null);
        if (planilla == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] contenido = planillaPdfService.generar(id);
            String nombre = String.format("planilla_%02d_%d.pdf",
                    planilla.getPeriodoMes() == null ? 0 : planilla.getPeriodoMes(),
                    planilla.getPeriodoAnio() == null ? 0 : planilla.getPeriodoAnio());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(contenido.length)
                    .body(contenido);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.internalServerError().body(error(e.getMessage()));
        }
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
    public ResponseEntity<?> descuentos(@PathVariable Long detalleId,
                                        @RequestParam(required = false) String tipos) {
        try {
            return ResponseEntity.ok(planillaService.conceptosDeDetalle(detalleId, tipos));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
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

    @GetMapping("/{id}/descuentos-matriz")
    public ResponseEntity<?> matrizDescuentos(@PathVariable Long id,
                                              @RequestParam(required = false) String tipos) {
        try {
            return ResponseEntity.ok(planillaService.obtenerMatrizPlanilla(id, tipos));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    @PutMapping("/{id}/descuentos-matriz")
    public ResponseEntity<?> guardarMatrizDescuentos(@PathVariable Long id,
                                                    @RequestBody List<DescuentoCeldaDTO> celdas) {
        try {
            planillaService.guardarMatrizPlanilla(id, celdas == null ? List.of() : celdas);
            return ResponseEntity.ok(Map.of("mensaje", "Descuentos masivos guardados y totales recalculados"));
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
