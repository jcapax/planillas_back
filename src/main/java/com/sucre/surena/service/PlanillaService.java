package com.sucre.surena.service;

import com.sucre.surena.dto.DescuentoCeldaDTO;
import com.sucre.surena.dto.DescuentoMatrizDTO;
import com.sucre.surena.dto.EmpleadoDescuentoDTO;
import com.sucre.surena.entity.*;
import com.sucre.surena.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
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
    private final ConfiguracionRepository configuracionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long nuevoDetalleId() {
        return ((Number) entityManager
                .createNativeQuery("SELECT nextval('planilla_detalle_id_seq')")
                .getSingleResult()).longValue();
    }

    /**
     * Códigos calculados automáticamente por el sistema: no se editan en la matriz.
     * Los HABER base / aportes por porcentaje se generan; los FIJO se aplican solos.
     */
    private static final Set<String> CODIGOS_SISTEMA = Set.of(
            "HABER_BASICO", "BONO_ANTIGUEDAD", "SALARIO_DOMINICAL",
            "AFP_10", "AFP_2_21", "APORTE_SOLIDARIO", "APORTE_NACIONAL");

    @Transactional
    public Planilla crearPlanilla(Integer anio, Integer mes) {
        planillaRepository.findByPeriodoAnioAndPeriodoMes(anio, mes)
                .ifPresent(p -> {
                    throw new IllegalArgumentException(
                            "Ya existe una planilla para " + mes + "/" + anio);
                });

        Empresa empresa = empresaRepository.findByActivoTrue().stream().findFirst().orElse(null);
        Number nuevoId = (Number) entityManager.createNativeQuery(
                "INSERT INTO planilla (periodo_anio, periodo_mes, nombre, empresa_id, estado, fecha_liquidacion) "
                + "VALUES (:anio, :mes, :nombre, :empresaId, :estado, :fecha) RETURNING id")
                .setParameter("anio", anio)
                .setParameter("mes", mes)
                .setParameter("nombre", nombrePeriodo(mes, anio))
                .setParameter("empresaId", empresa != null ? empresa.getId() : null)
                .setParameter("estado", Planilla.ESTADO_BORRADOR)
                .setParameter("fecha", LocalDate.of(anio, mes, YearMonth.of(anio, mes).atEndOfMonth().getDayOfMonth()))
                .getSingleResult();
        return planillaRepository.findById(nuevoId.longValue()).orElseThrow();
    }

    @Transactional
    public Planilla generar(Long planillaId) {
        Planilla planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada"));

        BigDecimal horasDia = valorParametro("HORAS_DIA", new BigDecimal("8"));
        BigDecimal diasMes = valorParametro("DIAS_MES", new BigDecimal("30"));
        BigDecimal dominicales = valorParametro("DOMINICALES", new BigDecimal("4"));
        BigDecimal aporteAfpPct = porcentajeParametro("APORTE_AFP");
        BigDecimal solidarioPct = porcentajeParametro("APORTE_SOLIDARIO");
        BigDecimal nacionalPct = porcentajeParametro("APORTE_NACIONAL");
        BigDecimal topeAporteNacional = valorParametro("TOPE_APORTE_NACIONAL", new BigDecimal("13000"));

        LocalDate finPeriodo = LocalDate.of(planilla.getPeriodoAnio(), planilla.getPeriodoMes(),
                YearMonth.of(planilla.getPeriodoAnio(), planilla.getPeriodoMes()).lengthOfMonth());

        Configuracion configuracion = configuracionRepository.findFirstByOrderByIdAsc().orElse(null);
        BigDecimal minimoNacional = (configuracion != null && configuracion.getMinimoNacional() != null)
                ? configuracion.getMinimoNacional() : BigDecimal.ZERO;
        BigDecimal cantidadMinimoNacional = (configuracion != null && configuracion.getCantidadMinimoNacional() != null)
                ? configuracion.getCantidadMinimoNacional() : BigDecimal.ONE;

        List<Empleado> empleados = empleadoRepository.findByActivoTrue(
                Sort.by(Sort.Direction.ASC, "persona.apellidoPaterno"));

        conceptoRepository.deleteByPlanillaId(planillaId);

        BigDecimal totalHaberes = BigDecimal.ZERO;
        BigDecimal totalDescuentos = BigDecimal.ZERO;

        int item = 1;
        for (Empleado empleado : empleados) {
            BigDecimal jornalHora = empleado.getJornalHora() != null ? empleado.getJornalHora() : BigDecimal.ZERO;

            BigDecimal horasTrabajadas = empleado.getHorasTrabajadas();
            if (horasTrabajadas == null || horasTrabajadas.compareTo(BigDecimal.ZERO) <= 0) {
                horasTrabajadas = (diasMes.subtract(dominicales)).multiply(horasDia);
            }
            // Haber básico: se toma de empleado.haber_basico (importe de la última
            // planilla generada / cargado a mano). Si está vacío se recurre al
            // cálculo histórico jornal_hora x horas_trabajadas.
            BigDecimal haberBasico = (empleado.getHaberBasico() != null
                    && empleado.getHaberBasico().compareTo(BigDecimal.ZERO) > 0)
                    ? empleado.getHaberBasico()
                    : jornalHora.multiply(horasTrabajadas).setScale(2, RoundingMode.HALF_UP);
            BigDecimal salarioDominical = jornalHora.multiply(horasDia).multiply(dominicales)
                    .setScale(2, RoundingMode.HALF_UP);

            long diasAntiguedad = 0;
            if (empleado.getFechaIngreso() != null) {
                diasAntiguedad = ChronoUnit.DAYS.between(empleado.getFechaIngreso(), finPeriodo);
            }
            BigDecimal bonoAntigPct = calcularBonoAntiguedad((int) diasAntiguedad);
            // Bono de antigüedad: minimo_nacional x cantidad_minimo_nacional x bono_antig_pct
            BigDecimal bonoAntigMonto = minimoNacional.multiply(cantidadMinimoNacional)
                    .multiply(bonoAntigPct)
                    .setScale(2, RoundingMode.HALF_UP);

            // Se registra en el empleado el haber básico usado en esta generación
            if (empleado.getHaberBasico() == null || haberBasico.compareTo(empleado.getHaberBasico()) != 0) {
                empleado.setHaberBasico(haberBasico);
                empleadoRepository.save(empleado);
            }

            // Conceptos variables propios del empleado (cualquier ingreso o descuento:
            // HABER extra, DESCUENTO variable, APORTE extra registrados en la matriz).
            Map<Concepto, BigDecimal> variablesEmpleado = conceptosVariablesEmpleado(empleado.getId());
            BigDecimal haberesExtra = sumarPorTipo(variablesEmpleado, Concepto.TIPO_HABER);
            BigDecimal aportesExtra = sumarPorTipo(variablesEmpleado, Concepto.TIPO_APORTE);
            BigDecimal descuentosFijos = sumarFijosGlobales();
            BigDecimal descuentosVariables = sumarPorTipo(variablesEmpleado, Concepto.TIPO_DESCUENTO);

            BigDecimal totalGanado = haberBasico.add(salarioDominical).add(bonoAntigMonto).add(haberesExtra);

            BigDecimal aporteAfp = totalGanado.multiply(aporteAfpPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteRiesgo = totalGanado.multiply(riesgoComunPctPara(empleado, finPeriodo)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteSolidario = totalGanado.multiply(solidarioPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteNacional = calcularAporteNacional(totalGanado, nacionalPct, topeAporteNacional);
            BigDecimal totalAportes = aporteAfp.add(aporteRiesgo).add(aporteSolidario).add(aporteNacional)
                    .add(aportesExtra);

            BigDecimal descuentosVarios = descuentosFijos.add(descuentosVariables);
            BigDecimal totalDescuentosDet = totalAportes.add(descuentosVarios);
            BigDecimal liquido = totalGanado.subtract(totalDescuentosDet);

            int currentItem = item++;
            Long detalleId = nuevoDetalleId();
            final BigDecimal horasF = horasTrabajadas;
            final Integer diasF = (int) diasAntiguedad;
            guardarConceptos(detalleId, planillaId, empleado.getId(), currentItem, horasTrabajadas,
                    jornalHora, diasF, bonoAntigPct, totalGanado, aporteAfp,
                    aporteRiesgo, aporteSolidario, aporteNacional, haberBasico, bonoAntigMonto,
                    salarioDominical);
            guardarFijos(detalleId, planillaId, empleado.getId(), currentItem, horasF,
                    jornalHora, diasF, bonoAntigPct);
            variablesEmpleado.forEach((concepto, monto) -> guardarConcepto(detalleId, planillaId,
                    empleado.getId(), currentItem, horasF, jornalHora, diasF,
                    bonoAntigPct, concepto, monto));

            totalHaberes = totalHaberes.add(totalGanado);
            totalDescuentos = totalDescuentos.add(totalDescuentosDet);
        }

        return planillaRepository.findById(planillaId).orElseThrow();
    }

    @Transactional
    public void eliminar(Long planillaId) {
        conceptoRepository.deleteByPlanillaId(planillaId);
        entityManager.createNativeQuery("DELETE FROM planilla WHERE id = :id")
                .setParameter("id", planillaId)
                .executeUpdate();
    }

    /**
     * Edita los conceptos variables (cualquier ingreso o descuento) de un empleado
     * dentro de una planilla concreta. Los descuentos fijos y los calculados por el
     * sistema no se tocan. Tras guardar, recalcula totales del detalle y de la planilla.
     */
    @Transactional
    public PlanillaDetalle actualizarDescuentos(Long detalleId, List<EmpleadoDescuentoDTO> descuentos) {
        PlanillaDetalle detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new IllegalArgumentException("Detalle de planilla no encontrado"));

        Map<Long, Concepto> editables = conceptosEditablesPorId();

        conceptoRepository.deleteByPlanillaDetalleIdAndConceptoIdIn(detalleId, editables.keySet());

        for (EmpleadoDescuentoDTO dto : descuentos) {
            if (dto.conceptoId() == null || dto.monto() == null
                    || dto.monto().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Concepto concepto = editables.get(dto.conceptoId());
            if (concepto == null) continue;
            conceptoRepository.save(PlanillaDetalleConcepto.builder()
                    .planillaDetalleId(detalleId)
                    .planillaId(detalle.getPlanilla().getId())
                    .empleadoId(detalle.getEmpleado().getId())
                    .item(detalle.getItem())
                    .horasTrabajadas(detalle.getHorasTrabajadas())
                    .jornalHora(detalle.getJornalHora())
                    .diasAntiguedad(detalle.getDiasAntiguedad())
                    .bonoAntigPct(detalle.getBonoAntigPct())
                    .concepto(concepto)
                    .tipo(concepto.getTipo())
                    .monto(dto.monto())
                    .build());
        }

        recalcularDetalle(detalle);
        return detalle;
    }

    /**
     * Vuelve a recalcular los campos total_aportes, descuentos_varios,
     * total_descuentos y liquido_pagable de cada fila de v_planilla_detalle
     * (además del total_ganado y los totales de la planilla), a partir de los
     * conceptos ya guardados. Idempotente: no borra ajustes del mes.
     */
    @Transactional
    public Planilla recalcular(Long planillaId) {
        Planilla planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada"));
        List<PlanillaDetalle> detalles = detalleRepository.findByPlanillaIdOrderByItemAsc(planilla.getId());
        if (detalles.isEmpty()) {
            throw new IllegalArgumentException("La planilla aún no ha sido generada");
        }
        for (PlanillaDetalle detalle : detalles) {
            recalcularDetalle(detalle);
        }
        return planilla;
    }

    // ------------------------------------------------------------------
    // Matriz de ingresos y descuentos (vista tipo Excel: todos los empleados
    // en filas, los tipos de concepto en columnas paralelas).
    // Columnas editables: HABERES no calculados (ej. HORAS_EXTRA y otros
    // ingresos propios) y DESCUENTOS variables. Se excluyen los códigos del
    // sistema (Haber Básico, Bono, Dominical, AFP, aportes por %) y los
    // descuentos FIJO, que se aplican automáticamente a todos.
    // ------------------------------------------------------------------

    private Set<String> parseTipos(String tiposParam) {
        if (tiposParam == null || tiposParam.isBlank()) return Set.of();
        return Arrays.stream(tiposParam.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private List<Concepto> conceptosMatriz(String tiposParam) {
        Set<String> tipos = parseTipos(tiposParam);
        List<Concepto> base = tipos.isEmpty()
                ? conceptRepository.findByActivoTrueOrderByOrdenAsc()
                : conceptRepository.findByActivoTrueAndTipoInOrderByOrdenAsc(tipos);
        return base.stream()
                .filter(c -> !CODIGOS_SISTEMA.contains(c.getCodigo()))
                .filter(c -> {
                    if (Concepto.TIPO_DESCUENTO.equals(c.getTipo())) {
                        return !"FIJO".equals(c.getTipoDescuento());
                    }
                    return true;
                })
                .toList();
    }

    private Map<Long, Concepto> conceptosEditablesPorId() {
        return conceptosMatriz(null).stream()
                .collect(Collectors.toMap(Concepto::getId, c -> c));
    }

    private void validarEditable(Concepto concepto) {
        if (concepto == null || !Boolean.TRUE.equals(concepto.getActivo())) {
            throw new IllegalArgumentException("El concepto no existe o está inactivo");
        }
        if (CODIGOS_SISTEMA.contains(concepto.getCodigo())) {
            throw new IllegalArgumentException("El concepto " + concepto.getCodigo()
                    + " es calculado por el sistema y no se edita en la matriz");
        }
        if (Concepto.TIPO_DESCUENTO.equals(concepto.getTipo())
                && "FIJO".equals(concepto.getTipoDescuento())) {
            throw new IllegalArgumentException("El concepto " + concepto.getCodigo()
                    + " es FIJO y se aplica automáticamente a todos");
        }
    }

    private Map<Concepto, BigDecimal> conceptosVariablesEmpleado(Long empleadoId) {
        Map<Concepto, BigDecimal> vars = new LinkedHashMap<>();
        for (EmpleadoDescuento d : empleadoDescuentoRepository
                .findByEmpleadoIdOrderByConceptoOrdenAsc(empleadoId)) {
            if (d.getMonto() == null || d.getMonto().compareTo(BigDecimal.ZERO) <= 0
                    || d.getConcepto() == null
                    || !Boolean.TRUE.equals(d.getConcepto().getActivo())
                    || CODIGOS_SISTEMA.contains(d.getConcepto().getCodigo())) {
                continue;
            }
            if (Concepto.TIPO_DESCUENTO.equals(d.getConcepto().getTipo())
                    && "FIJO".equals(d.getConcepto().getTipoDescuento())) {
                continue;
            }
            vars.put(d.getConcepto(), d.getMonto());
        }
        return vars;
    }

    private BigDecimal sumarPorTipo(Map<Concepto, BigDecimal> montos, String tipo) {
        return montos.entrySet().stream()
                .filter(e -> tipo.equals(e.getKey().getTipo()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumarFijosGlobales() {
        return conceptRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "FIJO")
                .stream()
                .map(Concepto::getMonto)
                .filter(Objects::nonNull)
                .filter(m -> m.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void guardarFijos(Long detalleId, Long planillaId, Long empleadoId, Integer item,
                              BigDecimal horasTrabajadas, BigDecimal jornalHora,
                              Integer diasAntiguedad, BigDecimal bonoAntigPct) {
        for (Concepto c : conceptRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "FIJO")) {
            if (c.getMonto() != null && c.getMonto().compareTo(BigDecimal.ZERO) > 0) {
                guardarConcepto(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                        diasAntiguedad, bonoAntigPct, c, c.getMonto());
            }
        }
    }

    /**
     * Recalcula un detalle leyendo sus conceptos guardados (idempotente: se puede
     * llamar tras cada guardado masivo). Asegura los fijos materializados, suma los
     * haberes extra al total ganado, recalcula los aportes del sistema sobre el nuevo
     * total ganado y suma los aportes/descuentos variables.
     */
    private void recalcularDetalle(PlanillaDetalle detalle) {
        List<PlanillaDetalleConcepto> filas =
                conceptoRepository.findByPlanillaDetalleIdOrderByIdAsc(detalle.getId());

        Set<Long> presentes = filas.stream()
                .filter(p -> p.getConcepto() != null)
                .map(p -> p.getConcepto().getId())
                .collect(Collectors.toSet());
        for (Concepto fijo : conceptRepository
                .findByActivoTrueAndTipoAndTipoDescuentoOrderByOrdenAsc(Concepto.TIPO_DESCUENTO, "FIJO")) {
            if (fijo.getMonto() != null && fijo.getMonto().compareTo(BigDecimal.ZERO) > 0
                    && !presentes.contains(fijo.getId())) {
                PlanillaDetalleConcepto creado = conceptoRepository.save(PlanillaDetalleConcepto.builder()
                        .planillaDetalleId(detalle.getId())
                        .planillaId(detalle.getPlanilla().getId())
                        .empleadoId(detalle.getEmpleado().getId())
                        .item(detalle.getItem())
                        .horasTrabajadas(detalle.getHorasTrabajadas())
                        .jornalHora(detalle.getJornalHora())
                        .diasAntiguedad(detalle.getDiasAntiguedad())
                        .bonoAntigPct(detalle.getBonoAntigPct())
                        .concepto(fijo)
                        .tipo(Concepto.TIPO_DESCUENTO)
                        .monto(fijo.getMonto())
                        .build());
                filas.add(creado);
            }
        }

        BigDecimal haberesExtra = BigDecimal.ZERO;
        for (PlanillaDetalleConcepto pdc : filas) {
            if (pdc.getConcepto() == null || pdc.getMonto() == null) continue;
            if (CODIGOS_SISTEMA.contains(pdc.getConcepto().getCodigo())) continue;
            String tipo = pdc.getTipo() != null ? pdc.getTipo() : pdc.getConcepto().getTipo();
            if (Concepto.TIPO_HABER.equals(tipo)) haberesExtra = haberesExtra.add(pdc.getMonto());
        }

        BigDecimal haberBasico = nz(detalle.getHaberBasico());
        BigDecimal dominical = nz(detalle.getSalarioDominical());
        BigDecimal bono = nz(detalle.getBonoAntigMonto());
        BigDecimal totalGanado = haberBasico.add(dominical).add(bono).add(haberesExtra);

        BigDecimal aporteAfpPct = porcentajeParametro("APORTE_AFP");
        BigDecimal solidarioPct = porcentajeParametro("APORTE_SOLIDARIO");
        BigDecimal nacionalPct = porcentajeParametro("APORTE_NACIONAL");
        BigDecimal topeAporteNacional = valorParametro("TOPE_APORTE_NACIONAL", new BigDecimal("13000"));
        actualizarConceptoSistema(filas, detalle, "AFP_10",
                totalGanado.multiply(aporteAfpPct).setScale(2, RoundingMode.HALF_UP));
        actualizarConceptoSistema(filas, detalle, "AFP_2_21",
                totalGanado.multiply(riesgoComunPctPara(detalle.getEmpleado(), fechaCorteDetalle(detalle)))
                        .setScale(2, RoundingMode.HALF_UP));
        actualizarConceptoSistema(filas, detalle, "APORTE_SOLIDARIO",
                totalGanado.multiply(solidarioPct).setScale(2, RoundingMode.HALF_UP));
        actualizarConceptoSistema(filas, detalle, "APORTE_NACIONAL",
                calcularAporteNacional(totalGanado, nacionalPct, topeAporteNacional));
    }

    private void actualizarConceptoSistema(List<PlanillaDetalleConcepto> filas, PlanillaDetalle detalle,
                                           String codigo, BigDecimal monto) {
        PlanillaDetalleConcepto fila = filas.stream()
                .filter(p -> p.getConcepto() != null && codigo.equals(p.getConcepto().getCodigo()))
                .findFirst()
                .orElse(null);
        if (fila != null) {
            fila.setMonto(monto);
            conceptoRepository.save(fila);
            return;
        }
        conceptRepository.findByCodigo(codigo).ifPresent(concepto ->
                conceptoRepository.save(PlanillaDetalleConcepto.builder()
                        .planillaDetalleId(detalle.getId())
                        .planillaId(detalle.getPlanilla().getId())
                        .empleadoId(detalle.getEmpleado().getId())
                        .item(detalle.getItem())
                        .horasTrabajadas(detalle.getHorasTrabajadas())
                        .jornalHora(detalle.getJornalHora())
                        .diasAntiguedad(detalle.getDiasAntiguedad())
                        .bonoAntigPct(detalle.getBonoAntigPct())
                        .concepto(concepto)
                        .tipo(concepto.getTipo())
                        .monto(monto)
                        .build()));
    }

    private BigDecimal riesgoComunPctPara(Empleado empleado, LocalDate fechaCorte) {
        Configuracion configuracion = configuracionRepository.findFirstByOrderByIdAsc().orElse(null);
        if (configuracion != null && configuracion.getEdadRiesgoComun() != null
                && configuracion.getEdadRiesgoComunPct() != null
                && empleado != null && empleado.getPersona() != null
                && empleado.getPersona().getFechaNacimiento() != null) {
            int edad = Period.between(empleado.getPersona().getFechaNacimiento(), fechaCorte).getYears();
            if (edad >= configuracion.getEdadRiesgoComun()) {
                return configuracion.getEdadRiesgoComunPct();
            }
        }
        return porcentajeParametro("RIESGO_COMUN");
    }

    private LocalDate fechaCorteDetalle(PlanillaDetalle detalle) {
        Planilla pl = detalle.getPlanilla();
        return LocalDate.of(pl.getPeriodoAnio(), pl.getPeriodoMes(),
                YearMonth.of(pl.getPeriodoAnio(), pl.getPeriodoMes()).lengthOfMonth());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public List<com.sucre.surena.dto.EmpleadoConceptoDTO> conceptosDeEmpleado(Long empleadoId, String tipos) {
        if (!empleadoRepository.existsById(empleadoId)) {
            throw new IllegalArgumentException("Empleado no encontrado");
        }
        List<Concepto> conceptos = conceptosMatriz(tipos);
        Map<Long, BigDecimal> montos = empleadoDescuentoRepository
                .findByEmpleadoIdOrderByConceptoOrdenAsc(empleadoId).stream()
                .filter(d -> d.getConcepto() != null)
                .collect(Collectors.toMap(d -> d.getConcepto().getId(), EmpleadoDescuento::getMonto,
                        (a, b) -> b));
        return conceptos.stream()
                .map(c -> new com.sucre.surena.dto.EmpleadoConceptoDTO(c.getId(), c.getCodigo(),
                        c.getNombre(), c.getTipo(), montos.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public void guardarConceptosDeEmpleado(Long empleadoId, List<EmpleadoDescuentoDTO> dtos) {
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        Map<Long, Concepto> porId = conceptRepository.findAll().stream()
                .collect(Collectors.toMap(Concepto::getId, c -> c));
        Map<Long, BigDecimal> nuevos = new HashMap<>();
        for (EmpleadoDescuentoDTO dto : dtos) {
            if (dto.conceptoId() == null) continue;
            Concepto concepto = porId.get(dto.conceptoId());
            if (concepto == null) continue;
            validarEditable(concepto);
            BigDecimal monto = dto.monto() == null ? BigDecimal.ZERO : dto.monto();
            if (monto.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El monto no puede ser negativo");
            }
            nuevos.put(dto.conceptoId(), monto);
        }
        List<EmpleadoDescuento> actuales = empleadoDescuentoRepository
                .findByEmpleadoIdOrderByConceptoOrdenAsc(empleado.getId());
        List<EmpleadoDescuento> aGuardar = new ArrayList<>();
        for (EmpleadoDescuento actual : actuales) {
            if (actual.getConcepto() == null) continue;
            if (nuevos.containsKey(actual.getConcepto().getId())) continue;
            aGuardar.add(actual);
        }
        for (Map.Entry<Long, BigDecimal> e : nuevos.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
            aGuardar.add(EmpleadoDescuento.builder()
                    .empleado(empleado)
                    .concepto(porId.get(e.getKey()))
                    .monto(e.getValue())
                    .build());
        }
        empleadoDescuentoRepository.deleteByEmpleadoId(empleado.getId());
        empleadoDescuentoRepository.saveAll(aGuardar);
    }

    @Transactional(readOnly = true)
    public List<com.sucre.surena.dto.EmpleadoConceptoDTO> conceptosDeDetalle(Long detalleId, String tipos) {
        if (!detalleRepository.existsById(detalleId)) {
            throw new IllegalArgumentException("Detalle de planilla no encontrado");
        }
        List<Concepto> conceptos = conceptosMatriz(tipos);
        Map<Long, BigDecimal> montos = conceptoRepository.findByPlanillaDetalleIdOrderByIdAsc(detalleId)
                .stream()
                .filter(pdc -> pdc.getConcepto() != null)
                .collect(Collectors.toMap(pdc -> pdc.getConcepto().getId(),
                        PlanillaDetalleConcepto::getMonto, (a, b) -> b));
        return conceptos.stream()
                .map(c -> new com.sucre.surena.dto.EmpleadoConceptoDTO(c.getId(), c.getCodigo(),
                        c.getNombre(), c.getTipo(), montos.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DescuentoMatrizDTO.MatrizEmpleados obtenerMatrizEmpleados(String tipos) {
        List<Concepto> conceptos = conceptosMatriz(tipos);
        List<DescuentoMatrizDTO.ConceptoColumna> columnas = conceptos.stream()
                .map(c -> new DescuentoMatrizDTO.ConceptoColumna(
                        c.getId(), c.getCodigo(), c.getNombre(), c.getTipo()))
                .toList();

        List<Empleado> empleados = empleadoRepository.findByActivoTrue(
                Sort.by(Sort.Direction.ASC, "persona.apellidoPaterno",
                        "persona.apellidoMaterno", "persona.nombres"));

        Set<Long> columnaIds = conceptos.stream().map(Concepto::getId).collect(Collectors.toSet());
        List<EmpleadoDescuento> todos = empleadoDescuentoRepository.findAll();
        Map<Long, Map<Long, BigDecimal>> montosPorEmpleado = new HashMap<>();
        for (EmpleadoDescuento d : todos) {
            if (d.getEmpleado() == null || d.getConcepto() == null) continue;
            if (!columnaIds.contains(d.getConcepto().getId())) continue;
            montosPorEmpleado
                    .computeIfAbsent(d.getEmpleado().getId(), k -> new HashMap<>())
                    .put(d.getConcepto().getId(), d.getMonto());
        }

        List<DescuentoMatrizDTO.FilaEmpleado> filas = empleados.stream().map(e -> {
            Map<Long, BigDecimal> montos = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Concepto c : conceptos) {
                BigDecimal m = montosPorEmpleado.getOrDefault(e.getId(), Map.of())
                        .getOrDefault(c.getId(), BigDecimal.ZERO);
                montos.put(c.getId(), m);
                total = total.add(m);
            }
            return new DescuentoMatrizDTO.FilaEmpleado(e.getId(), e.getNombresCompletos(),
                    e.getPersona() != null ? e.getPersona().getNroDocumento() : null, montos, total);
        }).toList();

        return new DescuentoMatrizDTO.MatrizEmpleados(columnas, filas);
    }

    @Transactional
    public void guardarMatrizEmpleados(List<DescuentoCeldaDTO> celdas) {
        Map<Long, Concepto> porId = conceptRepository.findAll().stream()
                .collect(Collectors.toMap(Concepto::getId, c -> c));

        Map<Long, Map<Long, BigDecimal>> porEmpleado = new HashMap<>();
        for (DescuentoCeldaDTO celda : celdas) {
            if (celda.empleadoId() == null || celda.conceptoId() == null) continue;
            Concepto concepto = porId.get(celda.conceptoId());
            if (concepto == null) continue;
            validarEditable(concepto);
            BigDecimal monto = celda.monto() == null ? BigDecimal.ZERO : celda.monto();
            if (monto.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El monto no puede ser negativo");
            }
            porEmpleado.computeIfAbsent(celda.empleadoId(), k -> new HashMap<>())
                    .put(celda.conceptoId(), monto);
        }

        for (Map.Entry<Long, Map<Long, BigDecimal>> entry : porEmpleado.entrySet()) {
            Empleado empleado = empleadoRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado: " + entry.getKey()));
            // Se reemplazan solo los conceptos editables enviados; los fijos y los del
            // sistema nunca viven en empleado_descuento.
            Set<Long> enviados = entry.getValue().keySet();
            List<EmpleadoDescuento> actuales = empleadoDescuentoRepository
                    .findByEmpleadoIdOrderByConceptoOrdenAsc(empleado.getId());
            Map<Long, EmpleadoDescuento> conservados = new HashMap<>();
            for (EmpleadoDescuento actual : actuales) {
                if (actual.getConcepto() == null) continue;
                Long cid = actual.getConcepto().getId();
                if (enviados.contains(cid)) continue;
                if (CODIGOS_SISTEMA.contains(actual.getConcepto().getCodigo())) continue;
                if (Concepto.TIPO_DESCUENTO.equals(actual.getConcepto().getTipo())
                        && "FIJO".equals(actual.getConcepto().getTipoDescuento())) continue;
                conservados.put(cid, actual);
            }
            List<EmpleadoDescuento> aGuardar = new ArrayList<>(conservados.values());
            for (Map.Entry<Long, BigDecimal> mc : entry.getValue().entrySet()) {
                if (mc.getValue() == null || mc.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
                aGuardar.add(EmpleadoDescuento.builder()
                        .empleado(empleado)
                        .concepto(porId.get(mc.getKey()))
                        .monto(mc.getValue())
                        .build());
            }
            empleadoDescuentoRepository.deleteByEmpleadoId(empleado.getId());
            empleadoDescuentoRepository.saveAll(aGuardar);
        }
    }

    @Transactional(readOnly = true)
    public DescuentoMatrizDTO.MatrizPlanilla obtenerMatrizPlanilla(Long planillaId, String tipos) {
        Planilla planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada"));
        List<Concepto> conceptos = conceptosMatriz(tipos);
        List<DescuentoMatrizDTO.ConceptoColumna> columnas = conceptos.stream()
                .map(c -> new DescuentoMatrizDTO.ConceptoColumna(
                        c.getId(), c.getCodigo(), c.getNombre(), c.getTipo()))
                .toList();

        List<PlanillaDetalle> detalles = detalleRepository.findByPlanillaIdOrderByItemAsc(planilla.getId());
        List<Long> detalleIds = detalles.stream().map(PlanillaDetalle::getId).toList();
        Map<Long, Map<Long, BigDecimal>> montosPorDetalle = new HashMap<>();
        if (!detalleIds.isEmpty()) {
            for (PlanillaDetalleConcepto pdc : conceptoRepository.findByPlanillaDetalleIdInOrderByIdAsc(detalleIds)) {
                if (pdc.getConcepto() == null) continue;
                montosPorDetalle
                        .computeIfAbsent(pdc.getPlanillaDetalleId(), k -> new HashMap<>())
                        .put(pdc.getConcepto().getId(), pdc.getMonto());
            }
        }

        List<DescuentoMatrizDTO.Fila> filas = detalles.stream().map(d -> {
            Map<Long, BigDecimal> montos = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Concepto c : conceptos) {
                BigDecimal m = montosPorDetalle.getOrDefault(d.getId(), Map.of())
                        .getOrDefault(c.getId(), BigDecimal.ZERO);
                montos.put(c.getId(), m);
                total = total.add(m);
            }
            String nombre = d.getEmpleado() != null ? d.getEmpleado().getNombresCompletos() : "";
            String doc = (d.getEmpleado() != null && d.getEmpleado().getPersona() != null)
                    ? d.getEmpleado().getPersona().getNroDocumento() : null;
            return new DescuentoMatrizDTO.Fila(d.getId(), d.getItem(),
                    d.getEmpleado() != null ? d.getEmpleado().getId() : null,
                    nombre, doc, montos, total);
        }).toList();

        return new DescuentoMatrizDTO.MatrizPlanilla(columnas, filas);
    }

    @Transactional
    public void guardarMatrizPlanilla(Long planillaId, List<DescuentoCeldaDTO> celdas) {
        Planilla planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada"));

        Map<Long, Concepto> porId = conceptRepository.findAll().stream()
                .collect(Collectors.toMap(Concepto::getId, c -> c));

        Map<Long, Map<Long, BigDecimal>> porDetalle = new HashMap<>();
        for (DescuentoCeldaDTO celda : celdas) {
            if (celda.detalleId() == null || celda.conceptoId() == null) continue;
            Concepto concepto = porId.get(celda.conceptoId());
            if (concepto == null) continue;
            validarEditable(concepto);
            BigDecimal monto = celda.monto() == null ? BigDecimal.ZERO : celda.monto();
            if (monto.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El monto no puede ser negativo");
            }
            porDetalle.computeIfAbsent(celda.detalleId(), k -> new HashMap<>())
                    .put(celda.conceptoId(), monto);
        }

        for (Map.Entry<Long, Map<Long, BigDecimal>> entry : porDetalle.entrySet()) {
            Long detalleId = entry.getKey();
            PlanillaDetalle detalle = detalleRepository.findById(detalleId)
                    .orElseThrow(() -> new IllegalArgumentException("Detalle no encontrado: " + detalleId));

            conceptoRepository.deleteByPlanillaDetalleIdAndConceptoIdIn(detalleId, entry.getValue().keySet());

            for (Map.Entry<Long, BigDecimal> mc : entry.getValue().entrySet()) {
                if (mc.getValue() == null || mc.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
                Concepto concepto = porId.get(mc.getKey());
                if (concepto == null) continue;
                conceptoRepository.save(PlanillaDetalleConcepto.builder()
                        .planillaDetalleId(detalleId)
                        .planillaId(detalle.getPlanilla().getId())
                        .empleadoId(detalle.getEmpleado().getId())
                        .item(detalle.getItem())
                        .horasTrabajadas(detalle.getHorasTrabajadas())
                        .jornalHora(detalle.getJornalHora())
                        .diasAntiguedad(detalle.getDiasAntiguedad())
                        .bonoAntigPct(detalle.getBonoAntigPct())
                        .concepto(concepto)
                        .tipo(concepto.getTipo())
                        .monto(mc.getValue())
                        .build());
            }

            recalcularDetalle(detalle);
        }

    }

    private void guardarConceptos(Long detalleId, Long planillaId, Long empleadoId, Integer item,
                                  BigDecimal horasTrabajadas, BigDecimal jornalHora,
                                  Integer diasAntiguedad, BigDecimal bonoAntigPct, BigDecimal totalGanado,
                                  BigDecimal aporteAfp, BigDecimal aporteRiesgo,
                                  BigDecimal aporteSolidario, BigDecimal aporteNacional,
                                  BigDecimal haberBasico, BigDecimal bonoAntigMonto,
                                  BigDecimal salarioDominical) {
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "HABER_BASICO", haberBasico);
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "BONO_ANTIGUEDAD", bonoAntigMonto);
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "SALARIO_DOMINICAL", salarioDominical);
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "AFP_10", aporteAfp);
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "AFP_2_21", aporteRiesgo);
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "APORTE_SOLIDARIO", aporteSolidario);
        guardarConceptoPorCodigo(detalleId, planillaId, empleadoId, item, horasTrabajadas, jornalHora,
                diasAntiguedad, bonoAntigPct, "APORTE_NACIONAL", aporteNacional);
    }

    private void guardarConceptoPorCodigo(Long detalleId, Long planillaId, Long empleadoId, Integer item,
                                          BigDecimal horasTrabajadas, BigDecimal jornalHora,
                                          Integer diasAntiguedad, BigDecimal bonoAntigPct,
                                          String codigo, BigDecimal monto) {
        conceptRepository.findByCodigo(codigo)
                .ifPresent(concepto -> guardarConcepto(detalleId, planillaId, empleadoId, item,
                        horasTrabajadas, jornalHora, diasAntiguedad, bonoAntigPct, concepto, monto));
    }

    private void guardarConcepto(Long detalleId, Long planillaId, Long empleadoId, Integer item,
                                 BigDecimal horasTrabajadas, BigDecimal jornalHora,
                                 Integer diasAntiguedad, BigDecimal bonoAntigPct,
                                 Concepto concepto, BigDecimal monto) {
        if (monto != null && monto.compareTo(BigDecimal.ZERO) != 0) {
            conceptoRepository.save(PlanillaDetalleConcepto.builder()
                    .planillaDetalleId(detalleId)
                    .planillaId(planillaId)
                    .empleadoId(empleadoId)
                    .item(item)
                    .horasTrabajadas(horasTrabajadas)
                    .jornalHora(jornalHora)
                    .diasAntiguedad(diasAntiguedad)
                    .bonoAntigPct(bonoAntigPct)
                    .concepto(concepto)
                    .tipo(concepto.getTipo())
                    .monto(monto)
                    .build());
        }
    }

    /**
     * Conceptos propios del empleado registrados en la matriz (cualquier ingreso o
     * descuento): haberes extra, descuentos variables y aportes extra. Los fijos y
     * los calculados por el sistema no viven aquí.
     * (Reemplaza al anterior calcularDescuentos, limitado a descuentos.)
     */
    private Map<Concepto, BigDecimal> calcularDescuentos(Empleado empleado) {
        return conceptosVariablesEmpleado(empleado.getId());
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
