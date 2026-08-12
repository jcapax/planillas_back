package com.sucre.surena.dto;

import java.math.BigDecimal;

public record EmpleadoDescuentoDTO(Long conceptoId, String codigo, String nombre, BigDecimal monto) {
}
