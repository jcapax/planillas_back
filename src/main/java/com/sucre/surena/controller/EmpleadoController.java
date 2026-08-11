package com.sucre.surena.controller;

import com.sucre.surena.entity.Empleado;
import com.sucre.surena.repository.EmpleadoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoRepository empleadoRepository;

    @GetMapping
    public Page<Empleado> listar(@RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "apellidoPaterno", "apellidoMaterno", "nombre1"));
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
        if (empleadoRepository.existsByTipoDocumentoAndNroDocumento(
                empleado.getTipoDocumento(), empleado.getNroDocumento())) {
            return ResponseEntity.badRequest().body(error(
                    "Ya existe un empleado con documento " + empleado.getNroDocumento()));
        }
        empleado.setId(null);
        empleado.setActivo(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(empleadoRepository.save(empleado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody Empleado datos) {
        return empleadoRepository.findById(id)
                .map(existente -> {
                    datos.setId(existente.getId());
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

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
