package com.sucre.surena.controller;

import com.sucre.surena.entity.Persona;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personas")
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;

    @GetMapping
    public Page<Persona> listar(@RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "apellidoPaterno", "apellidoMaterno", "nombres"));
        if (q != null && !q.isBlank()) {
            return personaRepository.buscar(q.trim(), pageable);
        }
        return personaRepository.findAll(pageable);
    }

    @GetMapping("/disponibles")
    public List<Persona> disponibles() {
        return personaRepository.findDisponibles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return personaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody Persona persona) {
        if (personaRepository.existsByTipoDocumentoAndNroDocumento(
                persona.getTipoDocumento(), persona.getNroDocumento())) {
            return ResponseEntity.badRequest().body(error(
                    "Ya existe una persona con documento " + persona.getNroDocumento()));
        }
        persona.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(personaRepository.save(persona));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody Persona datos) {
        return personaRepository.findById(id)
                .map(existente -> {
                    datos.setId(existente.getId());
                    return ResponseEntity.ok(personaRepository.save(datos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return personaRepository.findById(id)
                .map(persona -> {
                    if (empleadoRepository.existsByPersonaId(id)) {
                        return ResponseEntity.badRequest().body(error(
                                "No se puede eliminar: la persona está asociada a un empleado"));
                    }
                    personaRepository.delete(persona);
                    return ResponseEntity.ok(Map.of("mensaje", "Persona eliminada"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
