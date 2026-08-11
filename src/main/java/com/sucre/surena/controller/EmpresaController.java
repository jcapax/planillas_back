package com.sucre.surena.controller;

import com.sucre.surena.entity.Empresa;
import com.sucre.surena.repository.EmpresaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaRepository empresaRepository;

    @GetMapping
    public List<Empresa> listar() {
        return empresaRepository.findByActivoTrue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return empresaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody Empresa empresa) {
        empresa.setId(null);
        empresa.setActivo(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaRepository.save(empresa));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody Empresa datos) {
        return empresaRepository.findById(id)
                .map(existente -> {
                    datos.setId(existente.getId());
                    return ResponseEntity.ok(empresaRepository.save(datos));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        return empresaRepository.findById(id)
                .map(empresa -> {
                    empresa.setActivo(false);
                    empresaRepository.save(empresa);
                    Map<String, Object> body = new HashMap<>();
                    body.put("mensaje", "Empresa eliminada");
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
