package com.sucre.surena.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DescuentoMatrizDTO(
        List<ConceptoColumna> conceptos,
        List<Fila> filas) {

    public record ConceptoColumna(Long id, String codigo, String nombre, String tipo) {
    }

    public record FilaEmpleado(Long empleadoId, String nombre, String documento,
                               Map<Long, BigDecimal> montos, BigDecimal total) {
    }

    public record Fila(Long detalleId, Integer item, Long empleadoId, String nombre,
                       String documento, Map<Long, BigDecimal> montos, BigDecimal total) {
    }

    public record MatrizEmpleados(List<ConceptoColumna> conceptos, List<FilaEmpleado> filas) {
    }

    public record MatrizPlanilla(List<ConceptoColumna> conceptos, List<Fila> filas) {
    }
}
