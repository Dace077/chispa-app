package com.chispa.ingles.certificates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.domain.CertificateRules
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dibuja el diploma en PDF.
 *
 * Se usa `android.graphics.pdf.PdfDocument`, que viene en Android desde la API
 * 19. Cero dependencias nuevas, cero red: el archivo se genera entero en el
 * teléfono. Para una app que presume de no poder conectarse, meter una librería
 * de PDF de 2 MB para esto habría sido absurdo.
 *
 * El PDF no se guarda en la base de datos: se regenera cuando hace falta a
 * partir de la fila de `certificate`. Un PDF pesa lo suyo y los datos que lo
 * producen caben en una fila.
 *
 * ---
 *
 * **Por qué se ve así.** La primera versión llevaba doble marco, adornos en las
 * esquinas y las cifras metidas en cajitas. Parecía un certificado de curso
 * online, que es exactamente lo que no queremos que parezca.
 *
 * Los diplomas universitarios de verdad hacen lo contrario: son austeros. Papel
 * color hueso, una sola familia serif, cuerpos muy grandes, márgenes enormes y
 * ni un solo adorno. El peso se lo da el aire y la tipografía, no la
 * decoración. Eso es lo que se imita aquí —el género del diploma académico, que
 * es de todos— con la identidad de Chispa: nuestro violeta, nuestra chispa en
 * el sello y nuestro nombre. Ningún escudo ni marca de ninguna universidad.
 */
object CertificatePdf {

    /** A4 apaisado en puntos PostScript (72 por pulgada), que es lo que usa PdfDocument. */
    private const val ANCHO = 842
    private const val ALTO = 595

    // Papel color hueso, tinta cálida casi negra. El violeta se reserva para el
    // nombre y el sello: en un diploma el color se usa con cuentagotas.
    private const val PAPEL = 0xFFFDFBF5.toInt()
    private const val TINTA = 0xFF1A1410.toInt()
    private const val TINTA_SUAVE = 0xFF6E6455.toInt()
    private const val TINTA_TENUE = 0xFF9A9082.toInt()
    private const val VIOLETA = 0xFF3B2CA8.toInt()
    private const val ORO = 0xFFA9853F.toInt()

    private val SERIF: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val SERIF_ITALIC: Typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)

    data class Data(
        val studentName: String,
        val level: CefrLevel,
        val folio: String,
        val issuedAt: Long,
        val city: String,
        val lessonsCompleted: Int,
        val accuracy: Int,
        val totalXp: Int
    )

    /**
     * Genera el PDF en la caché de la app y devuelve el archivo.
     *
     * Va a `cacheDir` a propósito: es privado de la app, no necesita permisos de
     * almacenamiento y el sistema puede limpiarlo. Para que salga de ahí está
     * [CertificateSharing], que lo entrega vía FileProvider.
     */
    fun render(context: Context, data: Data): File {
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(ANCHO, ALTO, 1).create()
        val page = doc.startPage(info)
        dibujar(page.canvas, data)
        doc.finishPage(page)

        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val archivo = File(dir, "${nombreArchivo(data)}.pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    fun nombreArchivo(data: Data): String {
        val limpio = data.studentName.trim()
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(" ", "_")
            .ifBlank { "alumno" }
        return "Chispa_${data.level.label}_$limpio"
    }

    /* ===================================================================== */

    private fun dibujar(canvas: Canvas, data: Data) {
        canvas.drawColor(PAPEL)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = ANCHO / 2f

        // --- Filete doble: fino-grueso, la única línea del documento --------
        p.style = Paint.Style.STROKE
        p.color = ORO
        p.strokeWidth = 1.8f
        canvas.drawRect(30f, 30f, ANCHO - 30f, ALTO - 30f, p)
        p.strokeWidth = 0.6f
        canvas.drawRect(37f, 37f, ANCHO - 37f, ALTO - 37f, p)
        p.style = Paint.Style.FILL

        // --- Encabezado -----------------------------------------------------
        p.typeface = SERIF_BOLD
        p.color = TINTA
        p.textSize = 23f
        p.letterSpacing = 0.34f
        texto(canvas, "CHISPA", cx + 4f, 96f, p)   // +4 compensa el espaciado final

        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.7f
        p.color = ORO
        canvas.drawLine(cx - 42f, 108f, cx + 42f, 108f, p)
        p.style = Paint.Style.FILL

        p.typeface = SERIF
        p.color = TINTA_SUAVE
        p.textSize = 8f
        p.letterSpacing = 0.26f
        texto(canvas, "APRENDE INGLÉS SIN EXCUSAS", cx + 2f, 124f, p)
        p.letterSpacing = 0f

        // --- Fórmula --------------------------------------------------------
        p.typeface = SERIF_ITALIC
        p.color = TINTA_SUAVE
        p.textSize = 15f
        texto(canvas, "hace constar que", cx, 168f, p)

        // --- Nombre: el elemento más grande de la página --------------------
        val nombre = data.studentName.trim().ifBlank { "Alumno de Chispa" }
        p.typeface = SERIF_BOLD
        p.color = VIOLETA
        p.textSize = tamanoQueCabe(nombre, p, maximo = 43f, minimo = 21f, ancho = ANCHO - 190f)
        texto(canvas, nombre, cx, 218f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.7f
        p.color = ORO
        canvas.drawLine(cx - 150f, 236f, cx + 150f, 236f, p)
        p.style = Paint.Style.FILL

        // --- Cuerpo ---------------------------------------------------------
        p.typeface = SERIF
        p.color = TINTA
        p.textSize = 13f
        texto(canvas, "ha completado en su totalidad el programa de estudios correspondiente al", cx, 266f, p)

        p.typeface = SERIF_BOLD
        p.textSize = 26f
        p.letterSpacing = 0.07f
        p.color = TINTA
        texto(canvas, "NIVEL ${data.level.label}", cx + 3f, 303f, p)
        p.letterSpacing = 0f

        p.typeface = SERIF_ITALIC
        p.textSize = 15f
        p.color = TINTA
        texto(canvas, subtituloDelNivel(data.level), cx, 324f, p)

        p.typeface = SERIF
        p.textSize = 10.5f
        p.color = TINTA_SUAVE
        texto(canvas, "del Marco Común Europeo de Referencia para las Lenguas", cx, 344f, p)

        p.typeface = SERIF_ITALIC
        p.textSize = 10.5f
        p.color = TINTA_SUAVE
        parrafo(canvas, CertificateRules.descripcion(data.level), cx, 372f, 520f, 14f, p)

        // --- Dedicatoria ----------------------------------------------------
        val frase = CertificateRules.frase(data.level, data.studentName, data.folio)
        if (frase.isNotBlank()) {
            p.typeface = SERIF_ITALIC
            p.textSize = 12f
            p.color = VIOLETA
            texto(canvas, frase, cx, 424f, p)
        }

        // --- Cifras, en una sola línea discreta -----------------------------
        p.typeface = SERIF
        p.textSize = 9f
        p.letterSpacing = 0.1f
        p.color = TINTA_TENUE
        texto(
            canvas,
            "${data.lessonsCompleted} LECCIONES   ·   ${data.accuracy}% DE PRECISIÓN   ·   ${data.totalXp} XP",
            cx + 2f, 442f, p
        )
        p.letterSpacing = 0f

        // --- Lugar y fecha --------------------------------------------------
        p.typeface = SERIF
        p.textSize = 11f
        p.color = TINTA
        val lugar = if (data.city.isBlank()) "Dado a " else "Dado en ${data.city.trim()}, a "
        texto(canvas, lugar + fechaLarga(data.issuedAt), cx, 470f, p)

        // --- Sello y firma, enfrentados a los lados -------------------------
        sello(canvas, p, 172f, 498f)
        firma(canvas, p, 670f, 500f)

        // --- Pie: por debajo del sello y de la firma, nunca encima ----------
        p.typeface = SERIF
        p.textSize = 7.5f
        p.color = TINTA_TENUE
        texto(canvas, "Folio ${data.folio}", cx, 541f, p)

        // El ancho es casi el de la caja entera para que el aviso quepa en una
        // sola línea: con dos, la segunda se salía del filete inferior.
        p.textSize = 6.6f
        parrafo(canvas, CertificateRules.AVISO_LEGAL, cx, 552f, ANCHO - 110f, 8.5f, p)
    }

    /**
     * Sello en seco: dos aros y la chispa dentro.
     *
     * Sin relleno de color y con trazo fino, para que parezca grabado en el
     * papel y no una pegatina.
     */
    private fun sello(canvas: Canvas, p: Paint, cx: Float, cy: Float) {
        p.style = Paint.Style.STROKE
        p.color = VIOLETA
        p.strokeWidth = 1.4f
        canvas.drawCircle(cx, cy, 30f, p)
        p.strokeWidth = 0.5f
        canvas.drawCircle(cx, cy, 25.5f, p)

        p.style = Paint.Style.FILL
        p.color = VIOLETA
        canvas.drawPath(chispa(cx, cy - 3f, 12f), p)

        p.typeface = SERIF
        p.textSize = 5.4f
        p.letterSpacing = 0.24f
        p.color = VIOLETA
        texto(canvas, "CHISPA", cx + 2f, cy + 17f, p)
        p.letterSpacing = 0f
    }

    /** Línea de firma. Firma el programa, no una persona: no inventamos rectores. */
    private fun firma(canvas: Canvas, p: Paint, cx: Float, y: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.7f
        p.color = TINTA_SUAVE
        canvas.drawLine(cx - 88f, y, cx + 88f, y, p)
        p.style = Paint.Style.FILL

        p.typeface = SERIF
        p.textSize = 9f
        p.color = TINTA
        texto(canvas, "Equipo Chispa", cx, y + 14f, p)

        p.typeface = SERIF_ITALIC
        p.textSize = 7.5f
        p.color = TINTA_TENUE
        texto(canvas, "Programa de inglés A1–C2", cx, y + 25f, p)
    }

    /** Estrella de cuatro puntas: la chispa de la marca. */
    private fun chispa(cx: Float, cy: Float, r: Float): Path = Path().apply {
        val d = r * 0.30f
        moveTo(cx, cy - r)
        cubicTo(cx + d, cy - d, cx + d, cy - d, cx + r, cy)
        cubicTo(cx + d, cy + d, cx + d, cy + d, cx, cy + r)
        cubicTo(cx - d, cy + d, cx - d, cy + d, cx - r, cy)
        cubicTo(cx - d, cy - d, cx - d, cy - d, cx, cy - r)
        close()
    }

    /* --------------------------- utilidades ----------------------------- */

    private fun texto(canvas: Canvas, s: String, x: Float, y: Float, p: Paint) {
        val previo = p.textAlign
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(s, x, y, p)
        p.textAlign = previo
    }

    /** Reduce el cuerpo de letra hasta que el texto quepa en el ancho dado. */
    private fun tamanoQueCabe(
        s: String,
        p: Paint,
        maximo: Float,
        minimo: Float,
        ancho: Float
    ): Float {
        var t = maximo
        p.textSize = t
        while (t > minimo && p.measureText(s) > ancho) {
            t -= 1f
            p.textSize = t
        }
        return t
    }

    /** Párrafo centrado con salto de línea por palabras. */
    private fun parrafo(
        canvas: Canvas,
        s: String,
        cx: Float,
        y: Float,
        ancho: Float,
        interlineado: Float,
        p: Paint
    ) {
        if (s.isBlank()) return
        val lineas = mutableListOf<String>()
        var actual = StringBuilder()

        s.split(" ").forEach { palabra ->
            val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (p.measureText(prueba) <= ancho) {
                actual = StringBuilder(prueba)
            } else {
                if (actual.isNotEmpty()) lineas += actual.toString()
                actual = StringBuilder(palabra)
            }
        }
        if (actual.isNotEmpty()) lineas += actual.toString()

        lineas.forEachIndexed { i, linea ->
            texto(canvas, linea, cx, y + interlineado * i, p)
        }
    }

    private fun subtituloDelNivel(level: CefrLevel): String = when (level) {
        CefrLevel.A1 -> "Acceso"
        CefrLevel.A2 -> "Plataforma"
        CefrLevel.B1 -> "Intermedio"
        CefrLevel.B2 -> "Intermedio alto"
        CefrLevel.C1 -> "Dominio operativo eficaz"
        CefrLevel.C2 -> "Maestría"
        CefrLevel.EXTRA -> "Módulo complementario"
    }

    private fun fechaLarga(millis: Long): String {
        val fecha = java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        val formato = DateTimeFormatter.ofPattern(
            "d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-MX")
        )
        return fecha.format(formato)
    }

    const val DIR = "certificados"
}
