package com.chispa.ingles.certificates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.chispa.ingles.domain.ToeflItp
import com.chispa.ingles.domain.ToeflResult
import com.chispa.ingles.domain.ToeflSection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Informe en PDF de un simulacro terminado.
 *
 * **No es un certificado y no se llama así en ninguna parte.** La constancia de
 * nivel ([CertificatePdf]) acredita algo que el alumno completó dentro del
 * curso; esto es la hoja de resultados de una práctica, y el puntaje es una
 * estimación nuestra, no la de ETS. Confundir las dos cosas sería darle a
 * alguien un papel que no vale nada en una ventanilla y que él cree que sí.
 *
 * Por eso se ve distinto a propósito: vertical en vez de apaisado, sin sello ni
 * firma, con pinta de informe y no de diploma. Sirve para llevárselo a un
 * profesor, para compararlo con el siguiente intento o para guardarlo.
 */
object ExamReportPdf {

    /** A4 vertical en puntos PostScript (72 por pulgada). */
    private const val ANCHO = 595
    private const val ALTO = 842

    private const val PAPEL = 0xFFFDFBF5.toInt()
    private const val TINTA = 0xFF1A1410.toInt()
    private const val TINTA_SUAVE = 0xFF6E6455.toInt()
    private const val TINTA_TENUE = 0xFF9A9082.toInt()
    private const val VIOLETA = 0xFF3B2CA8.toInt()
    private const val REGLA = 0xFFD9D2C4.toInt()

    private val SERIF: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val SERIF_ITALIC: Typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)

    data class Data(
        val studentName: String,
        val examTitle: String,
        val result: ToeflResult,
        val takenAt: Long
    )

    fun render(context: Context, data: Data): File {
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(ANCHO, ALTO, 1).create()
        val page = doc.startPage(info)
        dibujar(page.canvas, data)
        doc.finishPage(page)

        val dir = File(context.cacheDir, CertificatePdf.DIR).apply { mkdirs() }
        val archivo = File(dir, nombreArchivo(data) + ".pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    fun nombreArchivo(data: Data): String {
        val limpio = data.studentName.trim()
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(" ", "_")
            .ifBlank { "alumno" }
        val examen = data.examTitle.replace(Regex("[^\\p{L}\\p{N}]"), "")
        return "Chispa_" + examen + "_" + limpio
    }

    /* ===================================================================== */

    private fun dibujar(canvas: Canvas, data: Data) {
        canvas.drawColor(PAPEL)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = ANCHO / 2f
        val margen = 64f

        // --- Encabezado --------------------------------------------------
        p.typeface = SERIF_BOLD
        p.color = TINTA
        p.textSize = 20f
        centrado(canvas, "INFORME DE SIMULACRO", cx, 92f, p)

        p.typeface = SERIF
        p.textSize = 11f
        p.color = TINTA_SUAVE
        centrado(canvas, "Preparación TOEFL ITP · aplicación Chispa", cx, 112f, p)

        p.color = REGLA
        p.strokeWidth = 1f
        canvas.drawLine(margen, 130f, ANCHO - margen, 130f, p)

        // --- Quién y qué -------------------------------------------------
        p.typeface = SERIF
        p.textSize = 10f
        p.color = TINTA_TENUE
        canvas.drawText("ALUMNO", margen, 162f, p)

        p.typeface = SERIF_BOLD
        p.textSize = 17f
        p.color = TINTA
        canvas.drawText(data.studentName.ifBlank { "Alumno sin registrar" }, margen, 184f, p)

        p.typeface = SERIF
        p.textSize = 10f
        p.color = TINTA_TENUE
        canvas.drawText("PRUEBA", margen, 216f, p)
        canvas.drawText("FECHA", cx + 40f, 216f, p)

        p.textSize = 12f
        p.color = TINTA
        canvas.drawText(data.examTitle, margen, 236f, p)
        canvas.drawText(fechaLarga(data.takenAt), cx + 40f, 236f, p)

        // --- El número ---------------------------------------------------
        p.color = REGLA
        canvas.drawLine(margen, 268f, ANCHO - margen, 268f, p)

        p.typeface = SERIF
        p.textSize = 11f
        p.color = TINTA_SUAVE
        centrado(canvas, "Puntaje estimado", cx, 300f, p)

        p.typeface = SERIF_BOLD
        p.textSize = 64f
        p.color = VIOLETA
        centrado(canvas, data.result.total.toString(), cx, 366f, p)

        p.typeface = SERIF
        p.textSize = 11f
        p.color = TINTA_SUAVE
        centrado(
            canvas,
            "escala de " + ToeflItp.MIN_TOTAL + " a " + ToeflItp.MAX_TOTAL +
                "  ·  nivel aproximado " + ToeflItp.nivelAproximado(data.result.total),
            cx, 388f, p
        )

        // --- Detalle por sección -----------------------------------------
        var y = 440f
        p.typeface = SERIF
        p.textSize = 10f
        p.color = TINTA_TENUE
        canvas.drawText("SECCIÓN", margen, y, p)
        canvas.drawText("ACIERTOS", cx + 20f, y, p)
        canvas.drawText("ESCALA", ANCHO - margen - 52f, y, p)

        y += 10f
        p.color = REGLA
        canvas.drawLine(margen, y, ANCHO - margen, y, p)

        val filas = listOf(
            Triple(ToeflSection.LISTENING, data.result.listeningRaw, data.result.listeningScaled),
            Triple(ToeflSection.STRUCTURE, data.result.structureRaw, data.result.structureScaled),
            Triple(ToeflSection.READING, data.result.readingRaw, data.result.readingScaled)
        )
        filas.forEach { (seccion, aciertos, escala) ->
            y += 30f
            p.typeface = SERIF
            p.textSize = 12f
            p.color = TINTA
            canvas.drawText(seccion.subtitle, margen, y, p)
            canvas.drawText(aciertos.toString() + " de " + seccion.questions, cx + 20f, y, p)
            p.typeface = SERIF_BOLD
            canvas.drawText(escala.toString(), ANCHO - margen - 52f, y, p)

            y += 10f
            p.color = REGLA
            canvas.drawLine(margen, y, ANCHO - margen, y, p)
        }

        // --- Lectura del resultado ---------------------------------------
        y += 40f
        p.typeface = SERIF_ITALIC
        p.textSize = 12f
        p.color = TINTA
        parrafo(canvas, ToeflItp.resumen(data.result), margen, y, ANCHO - margen * 2, p)

        // --- El aviso, que es la parte importante -------------------------
        p.color = REGLA
        canvas.drawLine(margen, ALTO - 150f, ANCHO - margen, ALTO - 150f, p)

        p.typeface = SERIF_BOLD
        p.textSize = 10f
        p.color = TINTA_SUAVE
        canvas.drawText("QUÉ ES Y QUÉ NO ES ESTE DOCUMENTO", margen, ALTO - 126f, p)

        p.typeface = SERIF
        p.textSize = 9f
        p.color = TINTA_SUAVE
        parrafo(canvas, AVISO, margen, ALTO - 108f, ANCHO - margen * 2, p)
    }

    /**
     * El texto que evita que esto se use para lo que no sirve.
     *
     * Va dentro del PDF y no solo en la pantalla porque el PDF es lo que acaba
     * reenviado por WhatsApp, ya sin nada de contexto alrededor.
     */
    private const val AVISO =
        "Este documento recoge el resultado de un simulacro de práctica realizado dentro de la " +
            "aplicación Chispa. No es un certificado, no procede de ETS ni de ningún organismo " +
            "acreditado y no acredita nivel alguno ante universidades, empleadores ni " +
            "autoridades. El puntaje es una estimación calculada por la aplicación: la tabla " +
            "oficial de conversión no es pública, así que sirve para medir el avance entre " +
            "simulacros, no para anticipar el resultado del examen real."

    /* ------------------------------ utilería ------------------------------ */

    private fun centrado(canvas: Canvas, s: String, cx: Float, y: Float, p: Paint) {
        canvas.drawText(s, cx - p.measureText(s) / 2f, y, p)
    }

    /** Escribe [s] partiendo por palabras y devuelve la y siguiente libre. */
    private fun parrafo(
        canvas: Canvas,
        s: String,
        x: Float,
        y: Float,
        ancho: Float,
        p: Paint
    ): Float {
        var linea = StringBuilder()
        var cursorY = y
        val alto = p.textSize * 1.45f
        s.split(' ').forEach { palabra ->
            val prueba = if (linea.isEmpty()) palabra else linea.toString() + " " + palabra
            if (p.measureText(prueba) > ancho && linea.isNotEmpty()) {
                canvas.drawText(linea.toString(), x, cursorY, p)
                cursorY += alto
                linea = StringBuilder(palabra)
            } else {
                linea = StringBuilder(prueba)
            }
        }
        if (linea.isNotEmpty()) {
            canvas.drawText(linea.toString(), x, cursorY, p)
            cursorY += alto
        }
        return cursorY
    }

    private fun fechaLarga(millis: Long): String =
        SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "MX")).format(Date(millis))
}
