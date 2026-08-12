package com.sucre.surena.dto;

import java.math.BigDecimal;
import java.util.List;

public record PapeletaDTO(
        Long detalleId,
        Integer item,
        String empleado,
        String documento,
        BigDecimal totalGanado,
        BigDecimal totalDescuentos,
        BigDecimal liquidoPagable,
        List<Linea> lineas
) {
    public record Linea(String codigo, String nombre, String tipo, BigDecimal monto) {}
}
