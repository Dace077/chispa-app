package com.chispa.ingles.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chispa.ingles.domain.Avatar
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Vuelca los avatares a PNG para poder VERLOS.
 *
 * Un avatar que compila no es un avatar que se parezca a un cerdo. Aquí no hay
 * assert que valga: el valor está en los archivos que deja en el dispositivo.
 *
 *   adb shell am instrument -w -e class com.chispa.ingles.ui.components.AvatarArtTest \
 *     com.chispa.ingles.debug.test/androidx.test.runner.AndroidJUnitRunner
 *   adb pull /sdcard/Android/data/com.chispa.ingles.debug/files/avatares
 */
@RunWith(AndroidJUnit4::class)
class AvatarArtTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(avatar: Avatar, mood: MascotMood, px: Int): Bitmap {
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap),
            Size(px.toFloat(), px.toFloat())
        ) {
            drawAvatarStatic(avatar, px.toFloat(), mood)
        }
        return bitmap.asAndroidBitmap()
    }

    @Test
    fun vuelca_todos_los_avatares() {
        val salida = File(context.getExternalFilesDir(null), "avatares").apply { mkdirs() }
        val dibujables = Avatar.entries.filter { it != Avatar.CHISPA }

        dibujables.forEach { avatar ->
            val bmp = render(avatar, MascotMood.NEUTRAL, 512)
            File(salida, "${avatar.id}.png").outputStream()
                .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

            // Comprobación mínima: que algo se pintó de verdad.
            assertTrue(
                "${avatar.id} salió en blanco",
                (0 until 512 step 16).any { x ->
                    (0 until 512 step 16).any { y -> bmp.getPixel(x, y) != 0 }
                }
            )
        }

        // Una hoja con los estados de ánimo, que es donde se ve si la cara funciona.
        val moods = listOf(
            MascotMood.NEUTRAL, MascotMood.HAPPY, MascotMood.SAD,
            MascotMood.THINKING, MascotMood.SLEEPY
        )
        val celda = 200
        val hoja = Bitmap.createBitmap(celda * moods.size, celda * dibujables.size, Bitmap.Config.ARGB_8888)
        val lienzo = android.graphics.Canvas(hoja)
        lienzo.drawColor(android.graphics.Color.parseColor("#EEEEF2"))
        dibujables.forEachIndexed { fila, avatar ->
            moods.forEachIndexed { col, mood ->
                lienzo.drawBitmap(render(avatar, mood, celda), celda * col.toFloat(), celda * fila.toFloat(), null)
            }
        }
        File(salida, "_hoja_animos.png").outputStream()
            .use { hoja.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
