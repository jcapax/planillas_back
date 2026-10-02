package com.sucre.surena.controller;

import com.sucre.surena.dto.DescuentoCeldaDTO;
import com.sucre.surena.dto.EmpleadoConceptoDTO;
import com.sucre.surena.dto.EmpleadoDescuentoDTO;
import com.sucre.surena.entity.Concepto;
import com.sucre.surena.entity.Empleado;
import com.sucre.surena.entity.EmpleadoDescuento;
import com.sucre.surena.entity.Persona;
import com.sucre.surena.repository.ConceptoRepository;
import com.sucre.surena.repository.EmpleadoDescuentoRepository;
import com.sucre.surena.repository.EmpleadoRepository;
import com.sucre.surena.repository.PersonaRepository;
import com.sucre.surena.service.PlanillaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/personal/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;
    private final EmpleadoDescuentoRepository empleadoDescuentoRepository;
    private final ConceptoRepository conceptoRepository;
    private final PlanillaService planillaService;

    @GetMapping
    public Page<Empleado> listar(@RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "persona.apellidoPaterno",
                        "persona.apellidoMaterno", "persona.nombres"));
        if (q != null && !q.isBlank()) {
            return empleadoRepository.buscar(q.trim(), pageable);
        }
        return empleadoRepository.findByActivoTrue(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return empleadoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody Empleado empleado) {
        if (empleado.getPersonaId() == null) {
            return ResponseEntity.badRequest().body(error("Seleccione una persona"));
        }
        Persona persona = personaRepository.findById(empleado.getPersonaId()).orElse(null);
        if (persona == null) {
            return ResponseEntity.badRequest().body(error("La persona seleccionada no existe"));
        }
        if (empleadoRepository.existsByPersonaId(persona.getId())) {
            return ResponseEntity.badRequest().body(error(
                    "La persona ya está registrada como empleado"));
        }
        empleado.setId(null);
        empleado.setPersona(persona);
        empleado.setActivo(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(empleadoRepository.save(empleado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody Empleado datos) {
        return empleadoRepository.findById(id)
                .map(existente -> {
                    datos.setId(existente.getId());
                    datos.setPersona(existente.getPersona());
                    return ResponseEntity.ok(empleadoRepository.save(datos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return empleadoRepository.findById(id)
                .map(empleado -> {
                    empleado.setActivo(false);
                    empleadoRepository.save(empleado);
                    return ResponseEntity.ok(Map.of("mensaje", "Empleado eliminado"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/descuentos")
    public ResponseEntity<?> descuentos(@PathVariable Long id,
                                        @RequestParam(required = false) String tipos) {
        if (!empleadoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<EmpleadoConceptoDTO> result = planillaService.conceptosDeEmpleado(id, tipos);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/descuentos")
    public ResponseEntity<?> guardarDescuentos(@PathVariable Long id,
                                               @Valid @RequestBody List<EmpleadoDescuentoDTO> descuentos) {
        Empleado empleado = empleadoRepository.findById(id).orElse(null);
        if (empleado == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            planillaService.guardarConceptosDeEmpleado(id, descuentos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Conceptos guardados"));
    }

    @GetMapping("/descuentos-matriz")
    public ResponseEntity<?> matrizDescuentos(@RequestParam(required = false) String tipos) {
        return ResponseEntity.ok(planillaService.obtenerMatrizEmpleados(tipos));
    }

    @PutMapping("/descuentos-matriz")
    public ResponseEntity<?> guardarMatrizDescuentos(@RequestBody List<DescuentoCeldaDTO> celdas) {
        try {
            planillaService.guardarMatrizEmpleados(celdas == null ? List.of() : celdas);
            return ResponseEntity.ok(Map.of("mensaje", "Descuentos masivos guardados"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
