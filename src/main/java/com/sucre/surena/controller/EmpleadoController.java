package com.sucre.surena.controller;

import com.sucre.surena.dto.EmpleadoDescuentoDTO;
import com.sucre.surena.entity.Concepto;
import com.sucre.surena.entity.Empleado;
import com.sucre.surena.entity.EmpleadoDescuento;
import com.sucre.surena.entity.Persona;
import com.sucre.surena.repository.ConceptoRepository;
import com.sucre.surena.repository.EmpleadoDescuentoRepository;
import com.sucre.surena.repository.EmpleadoRepository;
import com.sucre.surena.repository.PersonaRepository;
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
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;
    private final EmpleadoDescuentoRepository empleadoDescuentoRepository;
    private final ConceptoRepository conceptoRepository;

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
    public ResponseEntity<?> descuentos(@PathVariable Long id) {
        if (!empleadoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<Concepto> variables = conceptoRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "VARIABLE");
        Map<Long, BigDecimal> montos = empleadoDescuentoRepository.findByEmpleadoIdOrderByConceptoOrdenAsc(id)
                .stream()
                .collect(Collectors.toMap(d -> d.getConcepto().getId(), EmpleadoDescuento::getMonto));
        List<EmpleadoDescuentoDTO> result = variables.stream()
                .map(c -> new EmpleadoDescuentoDTO(c.getId(), c.getCodigo(), c.getNombre(),
                        montos.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/descuentos")
    public ResponseEntity<?> guardarDescuentos(@PathVariable Long id,
                                               @Valid @RequestBody List<EmpleadoDescuentoDTO> descuentos) {
        Empleado empleado = empleadoRepository.findById(id).orElse(null);
        if (empleado == null) {
            return ResponseEntity.notFound().build();
        }
        List<EmpleadoDescuento> aGuardar = new ArrayList<>();
        for (EmpleadoDescuentoDTO dto : descuentos) {
            if (dto.conceptoId() == null || dto.monto() == null || dto.monto().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Concepto concepto = conceptoRepository.findById(dto.conceptoId()).orElse(null);
            if (concepto == null || !Concepto.TIPO_DESCUENTO.equals(concepto.getTipo())
                    || !"VARIABLE".equals(concepto.getTipoDescuento())) {
                return ResponseEntity.badRequest().body(error(
                        "El concepto " + (concepto != null ? concepto.getCodigo() : dto.conceptoId())
                                + " no es un descuento variable"));
            }
            aGuardar.add(EmpleadoDescuento.builder()
                    .empleado(empleado)
                    .concepto(concepto)
                    .monto(dto.monto())
                    .build());
        }
        empleadoDescuentoRepository.deleteByEmpleadoId(id);
        empleadoDescuentoRepository.saveAll(aGuardar);
        return ResponseEntity.ok(Map.of("mensaje", "Descuentos guardados"));
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
