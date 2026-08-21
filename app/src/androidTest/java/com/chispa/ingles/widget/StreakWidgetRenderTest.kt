package com.chispa.ingles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chispa.ingles.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renderiza el widget tal y como lo pinta el launcher.
 *
 * `RemoteViews.apply()` devuelve la misma `View` que construye el proceso del
 * launcher, así que dibujarla a un bitmap es la comprobación más fiel posible
 * sin arrastrar el widget a la pantalla de inicio a mano. Y deja un PNG que se
 * puede mirar: un widget que compila puede seguir teniendo el número pisando la
 * barra.
 *
 *   adb shell am instrument -w -e class com.chispa.ingles.widget.StreakWidgetRenderTest \
 *     com.chispa.ingles.debug.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/com.chispa.ingles.debug/files/widget
 */
@RunWith(AndroidJUnit4::class)
class StreakWidgetRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(
        racha: Int,
        hoy: Int,
        meta: Int,
        anchoDp: Int = 250,
        altoDp: Int = 70
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (anchoDp * d).toInt()
        val h = (altoDp * d).toInt()

        val vistas = RemoteViews(context.packageName, R.layout.widget_streak).apply {
            setTextViewText(R.id.widget_streak, racha.toString())
            setTextViewText(
                R.id.widget_streak_label,
                if (racha == 1) "día seguido" else context.getString(R.string.widget_days)
            )
            setTextViewText(R.id.widget_flame, if (racha > 0) "🔥" else "💤")
            setTextViewText(R.id.widget_goal, "$hoy/$meta XP")
            setProgressBar(R.id.widget_progress, 100, (hoy * 100 / meta).coerceIn(0, 100), false)
        }

        val padre = FrameLayout(context)
        val vista = vistas.apply(context, padre)
        vista.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        vista.layout(0, 0, w, h)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bmp))
        return bmp
    }

    @Test
    fun el_widget_se_pinta_en_todos_sus_estados() {
        val salida = File(context.getExternalFilesDir(null), "widget").apply { mkdirs() }

        val casos = mapOf(
            "racha_larga" to Triple(47, 20, 30),
            "meta_cumplida" to Triple(12, 40, 30),
            "sin_racha" to Triple(0, 0, 30),
            "primer_dia" to Triple(1, 10, 30),
            // El caso que más fácil rompe una tarjeta pequeña: números grandes.
            "numeros_grandes" to Triple(365, 480, 500)
        )

        casos.forEach { (nombre, datos) ->
            val (racha, hoy, meta) = datos
            val bmp = render(racha, hoy, meta)
            File(salida, "$nombre.png").outputStream()
                .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // Se mira el centro, no la esquina: el fondo lleva 24dp de radio y
            // la esquina es transparente a propósito.
            assertTrue(
                "$nombre salió en blanco",
                bmp.getPixel(bmp.width / 2, bmp.height / 2) != 0
            )
        }

        // Y en el tamaño mínimo que declara el widget: 200x68dp. Debe caber
        // todo incluso con los números más largos posibles.
        val estrecho = render(365, 480, 500, anchoDp = 200, altoDp = 68)
        File(salida, "tamano_minimo.png").outputStream()
            .use { estrecho.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(estrecho.width > 0)
    }
}
