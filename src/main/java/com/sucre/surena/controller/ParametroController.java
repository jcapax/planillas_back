package com.sucre.surena.controller;

import com.sucre.surena.entity.Parametro;
import com.sucre.surena.repository.ParametroRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personal/parametros")
@RequiredArgsConstructor
public class ParametroController {

    private final ParametroRepository parametroRepository;

    @GetMapping
    public List<Parametro> listar() {
        return parametroRepository.findByActivoTrueOrderByCodigoAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return parametroRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody Parametro parametro) {
        if (parametroRepository.findByCodigo(parametro.getCodigo()).isPresent()) {
            return ResponseEntity.badRequest().body(error("Ya existe el parámetro: " + parametro.getCodigo()));
        }
        parametro.setId(null);
        parametro.setActivo(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(parametroRepository.save(parametro));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody Parametro datos) {
        return parametroRepository.findById(id)
                .map(existente -> {
                    datos.setId(existente.getId());
                    return ResponseEntity.ok(parametroRepository.save(datos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return parametroRepository.findById(id)
                .map(parametro -> {
                    parametro.setActivo(false);
                    parametroRepository.save(parametro);
                    return ResponseEntity.ok(Map.of("mensaje", "Parámetro eliminado"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
