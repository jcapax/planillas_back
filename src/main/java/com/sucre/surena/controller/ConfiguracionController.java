package com.sucre.surena.controller;

import com.sucre.surena.entity.Configuracion;
import com.sucre.surena.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/personal/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionRepository configuracionRepository;

    @GetMapping
    public ResponseEntity<Configuracion> obtener() {
        return ResponseEntity.ok(configuracionRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> configuracionRepository.save(Configuracion.builder().build())));
    }

    @PutMapping
    public ResponseEntity<?> guardar(@RequestBody Configuracion datos) {
        Configuracion configuracion = configuracionRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> Configuracion.builder().build());
        if (datos.getMinimoNacional() == null || datos.getMinimoNacional().signum() < 0) {
            return ResponseEntity.badRequest().body(error("El mínimo nacional no puede ser negativo"));
        }
        if (datos.getCantidadMinimoNacional() == null || datos.getCantidadMinimoNacional().signum() <= 0) {
            return ResponseEntity.badRequest().body(error("La cantidad de mínimos debe ser mayor a cero"));
        }
        configuracion.setMinimoNacional(datos.getMinimoNacional());
        configuracion.setCantidadMinimoNacional(datos.getCantidadMinimoNacional());
        return ResponseEntity.ok(configuracionRepository.save(configuracion));
    }

    private Map<String, String> error(String mensaje) {
        Map<String, String> body = new HashMap<>();
        body.put("error", mensaje);
        return body;
    }
}
