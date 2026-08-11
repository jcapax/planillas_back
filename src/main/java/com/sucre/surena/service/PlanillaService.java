package com.sucre.surena.service;

import com.sucre.surena.entity.*;
import com.sucre.surena.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanillaService {

    private final PlanillaRepository planillaRepository;
    private final PlanillaDetalleRepository detalleRepository;
    private final PlanillaDetalleConceptoRepository conceptoRepository;
    private final EmpleadoRepository empleadoRepository;
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

        LocalDate finPeriodo = LocalDate.of(planilla.getPeriodoAnio(), planilla.getPeriodoMes(),
                YearMonth.of(planilla.getPeriodoAnio(), planilla.getPeriodoMes()).lengthOfMonth());

        List<Empleado> empleados = empleadoRepository.findByActivoTrueOrderByApellidoPaternoAsc();

        conceptoRepository.deleteByPlanillaDetalle_PlanillaId(planillaId);
        detalleRepository.deleteByPlanillaId(planillaId);
        detalleRepository.flush();

        BigDecimal totalHaberes = BigDecimal.ZERO;
        BigDecimal totalDescuentos = BigDecimal.ZERO;

        int item = 1;
        for (Empleado empleado : empleados) {
            BigDecimal jornalHora = empleado.getJornalHora() != null ? empleado.getJornalHora() : BigDecimal.ZERO;

            BigDecimal horasTrabajadas = (diasMes.subtract(dominicales)).multiply(horasDia);
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
            BigDecimal aporteNacional = totalGanado.multiply(nacionalPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAportes = aporteAfp.add(aporteRiesgo).add(aporteSolidario).add(aporteNacional);

            BigDecimal descuentosVarios = BigDecimal.ZERO;
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

    private void guardarConceptos(PlanillaDetalle detalle, BigDecimal totalGanado,
                                  BigDecimal aporteAfp, BigDecimal aporteRiesgo,
                                  BigDecimal aporteSolidario, BigDecimal aporteNacional,
                                  BigDecimal haberBasico, BigDecimal bonoAntigMonto,
                                  BigDecimal salarioDominical) {
        guardarConcepto(detalle, "HABER_BASICO", Concepto.TIPO_HABER, haberBasico);
        guardarConcepto(detalle, "BONO_ANTIGUEDAD", Concepto.TIPO_HABER, bonoAntigMonto);
        guardarConcepto(detalle, "SALARIO_DOMINICAL", Concepto.TIPO_HABER, salarioDominical);
        guardarConcepto(detalle, "AFP_10", Concepto.TIPO_APORTE, aporteAfp);
        guardarConcepto(detalle, "AFP_2_21", Concepto.TIPO_APORTE, aporteRiesgo);
        guardarConcepto(detalle, "APORTE_SOLIDARIO", Concepto.TIPO_APORTE, aporteSolidario);
        guardarConcepto(detalle, "APORTE_NACIONAL", Concepto.TIPO_APORTE, aporteNacional);
    }

    private void guardarConcepto(PlanillaDetalle detalle, String codigo, String tipo, BigDecimal monto) {
        conceptRepository.findByCodigo(codigo).ifPresent(concepto -> {
            if (monto.compareTo(BigDecimal.ZERO) != 0) {
                conceptoRepository.save(PlanillaDetalleConcepto.builder()
                        .planillaDetalle(detalle)
                        .concepto(concepto)
                        .tipo(tipo)
                        .monto(monto)
                        .build());
            }
        });
    }

    private BigDecimal calcularBonoAntiguedad(int dias) {
        if (dias <= 0) return BigDecimal.ZERO;
        return bonoAntiguedadRepository
                .findFirstByActivoTrueAndDesdeDiasLessThanEqualOrderByDesdeDiasDesc(dias)
                .map(BonoAntiguedad::getPorcentaje)
                .orElse(BigDecimal.ZERO);
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
