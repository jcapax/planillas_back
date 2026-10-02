package com.sucre.surena.dto;

import java.math.BigDecimal;

public record DescuentoCeldaDTO(Long empleadoId, Long detalleId, Long conceptoId, BigDecimal monto) {
}
