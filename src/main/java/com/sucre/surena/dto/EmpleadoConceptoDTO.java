package com.sucre.surena.dto;

import java.math.BigDecimal;

public record EmpleadoConceptoDTO(Long conceptoId, String codigo, String nombre, String tipo, BigDecimal monto) {
}
