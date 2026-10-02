package com.sucre.surena.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sucre.surena.entity.Empresa;
import com.sucre.surena.entity.Planilla;
import com.sucre.surena.entity.PlanillaDetalle;
import com.sucre.surena.repository.PlanillaDetalleRepository;
import com.sucre.surena.repository.PlanillaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlanillaPdfService {

    private static final float CM = 28.3464567f;
    private static final float PAGE_WIDTH = 21f * CM;
    private static final float PAGE_HEIGHT = 33f * CM;
    private static final float MARGIN = 0.4f * CM;

    private static final String[] MESES = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
            "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};

    private static final String[] COLUMNAS = {
            "Nº", "Tipo Doc.", "Nº Documento", "Ext.", "AFP", "NUA/CUA",
            "Apellido Paterno", "Apellido Materno", "Nombre 1 / Otros", "País",
            "F. Nacimiento", "Sexo", "Jubilado", "Cargo", "F. Ingreso", "Horas",
            "Haber Básico", "Bono Antig.", "Otros Bonos", "Total Ganado",
            "Aportes AFP", "Otros Desc.", "Total Desc.", "Líquido", "Firma"
    };

    private static final float[] ANCHOS = {
            1.6f, 3f, 4.5f, 2f, 3.5f, 4.5f, 6f, 6f, 7.5f, 4f,
            4f, 1.5f, 2.5f, 8f, 4f, 3f, 4.5f, 4.5f, 4.5f, 4.5f,
            4.5f, 4.5f, 4.5f, 4.5f, 8f
    };

    private static final int[] ALINEACION = {
            Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT,
            Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_LEFT,
            Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT,
            Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT,
            Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT,
            Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT,
            Element.ALIGN_LEFT
    };

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Color COLOR_CABECERA = new Color(0xEC, 0xEF, 0xF3);
    private static final Color COLOR_FILA_PAR = new Color(0xF5, 0xF7, 0xFA);

    private final PlanillaRepository planillaRepository;
    private final PlanillaDetalleRepository detalleRepository;

    @Transactional(readOnly = true)
    public byte[] generar(Long planillaId) {
        Planilla planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new IllegalArgumentException("Planilla no encontrada"));
        List<PlanillaDetalle> detalles = detalleRepository.findByPlanillaIdOrderByItemAsc(planillaId);

        try {
            Document documento = new Document(new Rectangle(PAGE_WIDTH, PAGE_HEIGHT),
                    MARGIN, MARGIN, MARGIN, MARGIN);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, salida);
            documento.open();

            Empresa empresa = planilla.getEmpresa();
            if (empresa != null) {
                Font fEmpresa = fuente(6f, Font.NORMAL);
                if (empresa.getNombre() != null && !empresa.getNombre().isBlank()) {
                    documento.add(new Paragraph(empresa.getNombre(), fuente(6.5f, Font.BOLD)));
                }
                documento.add(new Paragraph(linea("CNSS", empresa.getCnss()), fEmpresa));
                documento.add(new Paragraph(linea("NIT", empresa.getNit()), fEmpresa));
                documento.add(new Paragraph(linea("Zona", empresa.getZona()), fEmpresa));
                documento.add(new Paragraph(linea("Calle", empresa.getCalle()), fEmpresa));
                if (empresa.getCiudad() != null && !empresa.getCiudad().isBlank()) {
                    documento.add(new Paragraph(empresa.getCiudad(), fEmpresa));
                }
                documento.add(new Paragraph(linea("Telf", empresa.getTelefono()), fEmpresa));
            }

            int mes = planilla.getPeriodoMes() != null ? planilla.getPeriodoMes() : 1;
            String titulo = "PLANILLA DE HABERES MES DE "
                    + MESES[Math.floorMod(mes - 1, 12)] + " " + planilla.getPeriodoAnio();
            Paragraph pTitulo = new Paragraph(titulo, fuente(9f, Font.BOLD));
            pTitulo.setAlignment(Element.ALIGN_CENTER);
            pTitulo.setSpacingBefore(6f);
            pTitulo.setSpacingAfter(4f);
            documento.add(pTitulo);

            documento.add(tabla(detalles));
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    private PdfPTable tabla(List<PlanillaDetalle> detalles) throws DocumentException {
        PdfPTable tabla = new PdfPTable(COLUMNAS.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(ANCHOS);
        tabla.setHeaderRows(1);
        tabla.setSplitRows(true);

        Font cabecera = fuente(4f, Font.BOLD);
        for (String columna : COLUMNAS) {
            PdfPCell celda = new PdfPCell(new Phrase(columna, cabecera));
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            celda.setBorderWidth(0.4f);
            celda.setBackgroundColor(COLOR_CABECERA);
            celda.setPadding(1.5f);
            tabla.addCell(celda);
        }

        Font cuerpo = fuente(4.5f, Font.NORMAL);
        Font negrita = fuente(4.5f, Font.BOLD);

        BigDecimal tHoras = BigDecimal.ZERO, tHaber = BigDecimal.ZERO, tBono = BigDecimal.ZERO,
                tOtros = BigDecimal.ZERO, tGanado = BigDecimal.ZERO, tAportes = BigDecimal.ZERO,
                tOtrosDesc = BigDecimal.ZERO, tTotalDesc = BigDecimal.ZERO, tLiquido = BigDecimal.ZERO;

        int indice = 0;
        for (PlanillaDetalle d : detalles) {
            Color fondo = indice % 2 == 1 ? COLOR_FILA_PAR : Color.WHITE;
            Object[] valores = fila(d);
            for (int i = 0; i < valores.length; i++) {
                tabla.addCell(celda(String.valueOf(valores[i]), cuerpo, ALINEACION[i], fondo));
            }

            tHoras = tHoras.add(nulo(d.getHorasTrabajadas()));
            tHaber = tHaber.add(nulo(d.getHaberBasico()));
            tBono = tBono.add(nulo(d.getBonoAntigMonto()));
            tOtros = tOtros.add(nulo(d.getSalarioDominical()));
            tGanado = tGanado.add(nulo(d.getTotalGanado()));
            tAportes = tAportes.add(nulo(d.getTotalAportes()));
            tOtrosDesc = tOtrosDesc.add(nulo(d.getDescuentosVarios()));
            tTotalDesc = tTotalDesc.add(nulo(d.getTotalDescuentos()));
            tLiquido = tLiquido.add(nulo(d.getLiquidoPagable()));
            indice++;
        }

        PdfPCell etiqueta = new PdfPCell(new Phrase("TOTALES", negrita));
        etiqueta.setColspan(15);
        etiqueta.setHorizontalAlignment(Element.ALIGN_LEFT);
        etiqueta.setVerticalAlignment(Element.ALIGN_MIDDLE);
        etiqueta.setBorderWidth(0.4f);
        etiqueta.setPadding(1.5f);
        tabla.addCell(etiqueta);

        Object[] totales = {num(tHoras), num(tHaber), num(tBono), num(tOtros), num(tGanado),
                num(tAportes), num(tOtrosDesc), num(tTotalDesc), num(tLiquido)};
        for (Object total : totales) {
            tabla.addCell(celda(String.valueOf(total), negrita, Element.ALIGN_RIGHT, Color.WHITE));
        }
        tabla.addCell(celda("", negrita, Element.ALIGN_LEFT, Color.WHITE));

        return tabla;
    }

    private Object[] fila(PlanillaDetalle d) {
        var empleado = d.getEmpleado();
        var persona = empleado != null ? empleado.getPersona() : null;
        return new Object[]{
                d.getItem() == null ? "" : d.getItem(),
                valor(persona != null ? persona.getTipoDocumento() : null),
                valor(persona != null ? persona.getNroDocumento() : null),
                valor(empleado != null ? empleado.getOrigen() : null),
                valor(empleado != null ? empleado.getAfp() : null),
                valor(empleado != null ? empleado.getNuaCua() : null),
                valor(persona != null ? persona.getApellidoPaterno() : null),
                valor(persona != null ? persona.getApellidoMaterno() : null),
                valor(persona != null ? persona.getNombres() : null),
                valor(persona != null ? persona.getPaisNacionalidad() : null),
                persona != null && persona.getFechaNacimiento() != null
                        ? persona.getFechaNacimiento().format(FECHA) : "",
                valor(persona != null ? persona.getSexo() : null),
                empleado != null && Boolean.TRUE.equals(empleado.getJubilado()) ? "Sí" : "No",
                valor(empleado != null ? empleado.getCargo() : null),
                empleado != null && empleado.getFechaIngreso() != null
                        ? empleado.getFechaIngreso().format(FECHA) : "",
                num(d.getHorasTrabajadas()),
                num(d.getHaberBasico()),
                num(d.getBonoAntigMonto()),
                num(d.getSalarioDominical()),
                num(d.getTotalGanado()),
                num(d.getTotalAportes()),
                num(d.getDescuentosVarios()),
                num(d.getTotalDescuentos()),
                num(d.getLiquidoPagable()),
                ""
        };
    }

    private PdfPCell celda(String texto, Font font, int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, font));
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setBorderWidth(0.4f);
        celda.setBackgroundColor(fondo);
        celda.setPadding(1.2f);
        celda.setMinimumHeight(11f);
        return celda;
    }

    private Font fuente(float tamano, int estilo) {
        try {
            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            return new Font(base, tamano, estilo);
        } catch (DocumentException | java.io.IOException e) {
            return new Font(Font.HELVETICA, tamano, estilo);
        }
    }

    private String linea(String etiqueta, String valor) {
        return etiqueta + ": " + (valor == null ? "" : valor);
    }

    private String valor(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private BigDecimal nulo(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String num(BigDecimal v) {
        return String.format(Locale.US, "%.2f", nulo(v));
    }
}
