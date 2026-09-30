package com.sucre.surena.service;

import com.sucre.surena.dto.EmpleadoDescuentoDTO;
import com.sucre.surena.entity.*;
import com.sucre.surena.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanillaService {

    private final PlanillaRepository planillaRepository;
    private final PlanillaDetalleRepository detalleRepository;
    private final PlanillaDetalleConceptoRepository conceptoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoDescuentoRepository empleadoDescuentoRepository;
    private final ParametroRepository parametroRepository;
    private final BonoAntiguedadRepository bonoAntiguedadRepository;
    private final ConceptoRepository conceptRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional
    public Planilla crearPlanilla(Integer anio, Integer mes) {
        planillaRepository.findByPeriodoAnioAndPeriodoMes(anio, mes)
                .ifPresent(p -> {
                    throw new IllegalArgumentException(
                            "Ya existe una planilla para " + mes + "/" + anio);
                });

        Empresa empresa = empresaRepository.findByActivoTrue().stream().findFirst().orElse(null);
        Planilla planilla = Planilla.builder()
                .periodoAnio(anio)
                .periodoMes(mes)
                .nombre(nombrePeriodo(mes, anio))
                .empresa(empresa)
                .estado(Planilla.ESTADO_BORRADOR)
                .fechaLiquidacion(LocalDate.of(anio, mes, YearMonth.of(anio, mes).atEndOfMonth().getDayOfMonth()))
                .totalHaberes(BigDecimal.ZERO)
                .totalDescuentos(BigDecimal.ZERO)
                .totalLiquido(BigDecimal.ZERO)
                .build();
        return planillaRepository.save(planilla);
    }

    @Transactional
    public Planilla generar(Long planillaId) {
        Planilla planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada"));

        BigDecimal horasDia = valorParametro("HORAS_DIA", new BigDecimal("8"));
        BigDecimal diasMes = valorParametro("DIAS_MES", new BigDecimal("30"));
        BigDecimal dominicales = valorParametro("DOMINICALES", new BigDecimal("4"));
        BigDecimal aporteAfpPct = porcentajeParametro("APORTE_AFP");
        BigDecimal riesgoComunPct = porcentajeParametro("RIESGO_COMUN");
        BigDecimal solidarioPct = porcentajeParametro("APORTE_SOLIDARIO");
        BigDecimal nacionalPct = porcentajeParametro("APORTE_NACIONAL");
        BigDecimal topeAporteNacional = valorParametro("TOPE_APORTE_NACIONAL", new BigDecimal("13000"));

        LocalDate finPeriodo = LocalDate.of(planilla.getPeriodoAnio(), planilla.getPeriodoMes(),
                YearMonth.of(planilla.getPeriodoAnio(), planilla.getPeriodoMes()).lengthOfMonth());

        List<Empleado> empleados = empleadoRepository.findByActivoTrue(
                Sort.by(Sort.Direction.ASC, "persona.apellidoPaterno"));

        conceptoRepository.deleteByPlanillaDetalle_PlanillaId(planillaId);
        detalleRepository.deleteByPlanillaId(planillaId);
        detalleRepository.flush();

        BigDecimal totalHaberes = BigDecimal.ZERO;
        BigDecimal totalDescuentos = BigDecimal.ZERO;

        int item = 1;
        for (Empleado empleado : empleados) {
            BigDecimal jornalHora = empleado.getJornalHora() != null ? empleado.getJornalHora() : BigDecimal.ZERO;

            BigDecimal horasTrabajadas = empleado.getHorasTrabajadas();
            if (horasTrabajadas == null || horasTrabajadas.compareTo(BigDecimal.ZERO) <= 0) {
                horasTrabajadas = (diasMes.subtract(dominicales)).multiply(horasDia);
            }
            BigDecimal haberBasico = jornalHora.multiply(horasTrabajadas).setScale(2, RoundingMode.HALF_UP);
            BigDecimal salarioDominical = jornalHora.multiply(horasDia).multiply(dominicales)
                    .setScale(2, RoundingMode.HALF_UP);

            long diasAntiguedad = 0;
            if (empleado.getFechaIngreso() != null) {
                diasAntiguedad = ChronoUnit.DAYS.between(empleado.getFechaIngreso(), finPeriodo);
            }
            BigDecimal bonoAntigPct = calcularBonoAntiguedad((int) diasAntiguedad);
            BigDecimal bonoAntigMonto = bonoAntigPct
                    .multiply(haberBasico.add(salarioDominical))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalGanado = haberBasico.add(salarioDominical).add(bonoAntigMonto);

            BigDecimal aporteAfp = totalGanado.multiply(aporteAfpPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteRiesgo = totalGanado.multiply(riesgoComunPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteSolidario = totalGanado.multiply(solidarioPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteNacional = calcularAporteNacional(totalGanado, nacionalPct, topeAporteNacional);
            BigDecimal totalAportes = aporteAfp.add(aporteRiesgo).add(aporteSolidario).add(aporteNacional);

            BigDecimal descuentosVarios = BigDecimal.ZERO;
            Map<Concepto, BigDecimal> descuentos = calcularDescuentos(empleado);
            for (BigDecimal monto : descuentos.values()) {
                descuentosVarios = descuentosVarios.add(monto);
            }
            BigDecimal totalDescuentosDet = totalAportes.add(descuentosVarios);
            BigDecimal liquido = totalGanado.subtract(totalDescuentosDet);

            PlanillaDetalle detalle = PlanillaDetalle.builder()
                    .planilla(planilla)
                    .empleado(empleado)
                    .item(item++)
                    .horasTrabajadas(horasTrabajadas)
                    .jornalHora(jornalHora)
                    .haberBasico(haberBasico)
                    .diasAntiguedad((int) diasAntiguedad)
                    .bonoAntigPct(bonoAntigPct)
                    .salarioDominical(salarioDominical)
                    .bonoAntigMonto(bonoAntigMonto)
                    .totalGanado(totalGanado)
                    .aporteSolidario(aporteSolidario)
                    .aporteNacional(aporteNacional)
                    .aporteAfp(aporteAfp)
                    .aporteRiesgoComun(aporteRiesgo)
                    .totalAportes(totalAportes)
                    .descuentosVarios(descuentosVarios)
                    .totalDescuentos(totalDescuentosDet)
                    .liquidoPagable(liquido)
                    .build();
            detalleRepository.save(detalle);

            guardarConceptos(detalle, totalGanado, aporteAfp, aporteRiesgo, aporteSolidario,
                    aporteNacional, haberBasico, bonoAntigMonto, salarioDominical);
            descuentos.forEach((concepto, monto) -> guardarConcepto(detalle, concepto, monto));

            totalHaberes = totalHaberes.add(totalGanado);
            totalDescuentos = totalDescuentos.add(totalDescuentosDet);
        }

        planilla.setTotalHaberes(totalHaberes);
        planilla.setTotalDescuentos(totalDescuentos);
        planilla.setTotalLiquido(totalHaberes.subtract(totalDescuentos));
        return planillaRepository.save(planilla);
    }

    @Transactional
    public void eliminar(Long planillaId) {
        conceptoRepository.deleteByPlanillaDetalle_PlanillaId(planillaId);
        detalleRepository.deleteByPlanillaId(planillaId);
        planillaRepository.deleteById(planillaId);
    }

    /**
     * Edita los descuentos VARIABLES de un empleado dentro de una planilla concreta.
     * Los descuentos fijos no se tocan: se aplican automáticamente a todos.
     * Tras guardar, recalcula totales del detalle y de la planilla.
     */
    @Transactional
    public PlanillaDetalle actualizarDescuentos(Long detalleId, List<EmpleadoDescuentoDTO> descuentos) {
        PlanillaDetalle detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new IllegalArgumentException("Detalle de planilla no encontrado"));

        List<Concepto> variables = conceptRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "VARIABLE");
        Set<Long> variableIds = variables.stream().map(Concepto::getId).collect(Collectors.toSet());

        conceptoRepository.deleteByPlanillaDetalleIdAndConceptoIdIn(detalleId, variableIds);

        Map<Concepto, BigDecimal> aplicados = new LinkedHashMap<>();
        for (EmpleadoDescuentoDTO dto : descuentos) {
            if (dto.conceptoId() == null || dto.monto() == null
                    || dto.monto().compareTo(BigDecimal.ZERO) <= 0 || !variableIds.contains(dto.conceptoId())) {
                continue;
            }
            Concepto concepto = conceptRepository.findById(dto.conceptoId()).orElse(null);
            if (concepto == null) continue;
            aplicados.put(concepto, dto.monto());
            conceptoRepository.save(PlanillaDetalleConcepto.builder()
                    .planillaDetalle(detalle)
                    .concepto(concepto)
                    .tipo(Concepto.TIPO_DESCUENTO)
                    .monto(dto.monto())
                    .build());
        }

        BigDecimal descuentosVarios = BigDecimal.ZERO;
        for (Concepto c : conceptRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "FIJO")) {
            if (c.getMonto() != null && c.getMonto().compareTo(BigDecimal.ZERO) > 0) {
                descuentosVarios = descuentosVarios.add(c.getMonto());
            }
        }
        for (BigDecimal monto : aplicados.values()) {
            descuentosVarios = descuentosVarios.add(monto);
        }

        BigDecimal totalDescuentos = detalle.getTotalAportes().add(descuentosVarios);
        detalle.setDescuentosVarios(descuentosVarios);
        detalle.setTotalDescuentos(totalDescuentos);
        detalle.setLiquidoPagable(detalle.getTotalGanado().subtract(totalDescuentos));
        detalleRepository.save(detalle);

        recalcularTotalesPlanilla(detalle.getPlanilla());
        return detalle;
    }

    private void recalcularTotalesPlanilla(Planilla planilla) {
        List<PlanillaDetalle> detalles = detalleRepository.findByPlanillaIdOrderByItemAsc(planilla.getId());
        BigDecimal totalHaberes = detalles.stream()
                .map(PlanillaDetalle::getTotalGanado)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDescuentos = detalles.stream()
                .map(PlanillaDetalle::getTotalDescuentos)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        planilla.setTotalHaberes(totalHaberes);
        planilla.setTotalDescuentos(totalDescuentos);
        planilla.setTotalLiquido(totalHaberes.subtract(totalDescuentos));
        planillaRepository.save(planilla);
    }

    private void guardarConceptos(PlanillaDetalle detalle, BigDecimal totalGanado,
                                  BigDecimal aporteAfp, BigDecimal aporteRiesgo,
                                  BigDecimal aporteSolidario, BigDecimal aporteNacional,
                                  BigDecimal haberBasico, BigDecimal bonoAntigMonto,
                                  BigDecimal salarioDominical) {
        guardarConceptoPorCodigo(detalle, "HABER_BASICO", haberBasico);
        guardarConceptoPorCodigo(detalle, "BONO_ANTIGUEDAD", bonoAntigMonto);
        guardarConceptoPorCodigo(detalle, "SALARIO_DOMINICAL", salarioDominical);
        guardarConceptoPorCodigo(detalle, "AFP_10", aporteAfp);
        guardarConceptoPorCodigo(detalle, "AFP_2_21", aporteRiesgo);
        guardarConceptoPorCodigo(detalle, "APORTE_SOLIDARIO", aporteSolidario);
        guardarConceptoPorCodigo(detalle, "APORTE_NACIONAL", aporteNacional);
    }

    private void guardarConceptoPorCodigo(PlanillaDetalle detalle, String codigo, BigDecimal monto) {
        conceptRepository.findByCodigo(codigo)
                .ifPresent(concepto -> guardarConcepto(detalle, concepto, monto));
    }

    private void guardarConcepto(PlanillaDetalle detalle, Concepto concepto, BigDecimal monto) {
        if (monto != null && monto.compareTo(BigDecimal.ZERO) != 0) {
            conceptoRepository.save(PlanillaDetalleConcepto.builder()
                    .planillaDetalle(detalle)
                    .concepto(concepto)
                    .tipo(concepto.getTipo())
                    .monto(monto)
                    .build());
        }
    }

    /**
     * Descuentos aplicables al empleado:
     *  - FIJO: monto configurado en el concepto, igual para todos los empleados.
     *  - VARIABLE: monto propio registrado en empleado_descuento.
     */
    private Map<Concepto, BigDecimal> calcularDescuentos(Empleado empleado) {
        Map<Concepto, BigDecimal> descuentos = new LinkedHashMap<>();

        for (Concepto c : conceptRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "FIJO")) {
            if (c.getMonto() != null && c.getMonto().compareTo(BigDecimal.ZERO) > 0) {
                descuentos.put(c, c.getMonto());
            }
        }

        for (EmpleadoDescuento d : empleadoDescuentoRepository
                .findByEmpleadoIdOrderByConceptoOrdenAsc(empleado.getId())) {
            if (d.getMonto() != null && d.getMonto().compareTo(BigDecimal.ZERO) > 0 && d.getConcepto() != null) {
                descuentos.put(d.getConcepto(), d.getMonto());
            }
        }
        return descuentos;
    }

    private BigDecimal calcularBonoAntiguedad(int dias) {
        if (dias <= 0) return BigDecimal.ZERO;
        return bonoAntiguedadRepository
                .findFirstByActivoTrueAndDesdeDiasLessThanEqualOrderByDesdeDiasDesc(dias)
                .map(BonoAntiguedad::getPorcentaje)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calcularAporteNacional(BigDecimal totalGanado, BigDecimal pct, BigDecimal tope) {
        if (totalGanado.compareTo(tope) < 0) {
            return BigDecimal.ZERO;
        }
        return totalGanado.subtract(tope)
                .multiply(pct)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal valorParametro(String codigo, BigDecimal defecto) {
        return parametroRepository.findByCodigo(codigo)
                .map(Parametro::getValor)
                .orElse(defecto);
    }

    private BigDecimal porcentajeParametro(String codigo) {
        return valorParametro(codigo, BigDecimal.ZERO);
    }

    public static String nombrePeriodo(int mes, int anio) {
        String[] nombres = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
                "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        return "Planilla de " + nombres[mes - 1] + " " + anio;
    }
}
