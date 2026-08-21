package com.chispa.ingles.certificates

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.domain.CertificateRules
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renderiza certificados de verdad en el dispositivo.
 *
 * El PDF se dibuja con `android.graphics`, que en la JVM de los tests unitarios
 * no existe (todos los métodos devuelven cero). Así que esta comprobación tiene
 * que correr en un emulador o un móvil.
 *
 * Además de comprobar que no revienta, deja los PDF y sus PNG en la carpeta de
 * archivos externos de la app para poder **mirarlos**. Un certificado que se
 * genera sin excepciones pero tiene el nombre encima del sello sigue siendo un
 * certificado roto, y eso no lo detecta ningún assert.
 *
 *   adb shell am instrument -w -e class com.chispa.ingles.certificates.CertificatePdfTest \
 *     com.chispa.ingles.debug.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/com.chispa.ingles.debug/files/muestras-certificado
 */
@RunWith(AndroidJUnit4::class)
class CertificatePdfTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun datos(
        nombre: String,
        level: CefrLevel,
        ciudad: String = "Guadalajara, Jalisco"
    ): CertificatePdf.Data {
        val folio = CertificateRules.folio(level, nombre, 20_680L)
        return CertificatePdf.Data(
            studentName = nombre,
            level = level,
            folio = folio,
            issuedAt = 1_786_000_000_000L,
            city = ciudad,
            lessonsCompleted = 15,
            accuracy = 94,
            totalXp = 1_240
        )
    }

    @Test
    fun genera_un_pdf_valido_para_cada_nivel() {
        val salida = File(context.getExternalFilesDir(null), "muestras-certificado")
            .apply { mkdirs() }

        CefrLevel.entries.filter { it != CefrLevel.EXTRA }.forEach { level ->
            val archivo = CertificatePdf.render(context, datos("María Fernanda Ruiz Delgado", level))

            assertTrue("el PDF de $level no existe", archivo.exists())
            assertTrue("el PDF de $level está vacío", archivo.length() > 1_000)

            // Que abra con el lector de PDF del sistema es la prueba de que el
            // archivo está bien formado y no solo de que se escribió algo.
            abrirYExportar(archivo, File(salida, "${level.label}.png"))
            archivo.copyTo(File(salida, "${level.label}.pdf"), overwrite = true)
        }
    }

    /**
     * Nombres extremos: el largo debe encogerse hasta caber y el corto no debe
     * descolocar el subrayado. Es el fallo más probable de todo el certificado.
     */
    @Test
    fun los_nombres_muy_largos_y_muy_cortos_siguen_cabiendo() {
        val salida = File(context.getExternalFilesDir(null), "muestras-certificado")
            .apply { mkdirs() }

        val casos = mapOf(
            "largo" to "María de los Ángeles Hernández de la Torre y Villaseñor",
            "corto" to "Ana Li",
            "sin_ciudad" to "Jorge Yu"
        )

        casos.forEach { (etiqueta, nombre) ->
            val data = datos(nombre, CefrLevel.B2, ciudad = if (etiqueta == "sin_ciudad") "" else "Monterrey")
            val archivo = CertificatePdf.render(context, data)
            assertTrue(archivo.length() > 1_000)
            abrirYExportar(archivo, File(salida, "nombre_$etiqueta.png"))
            archivo.copyTo(File(salida, "nombre_$etiqueta.pdf"), overwrite = true)
        }
    }

    /** Abre el PDF con PdfRenderer y vuelca la página a PNG para poder verla. */
    private fun abrirYExportar(pdf: File, png: File) {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertTrue("el PDF debe tener una página", renderer.pageCount == 1)
                renderer.openPage(0).use { page ->
                    val escala = 2
                    val bitmap = Bitmap.createBitmap(
                        page.width * escala,
                        page.height * escala,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
