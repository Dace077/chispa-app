package com.chispa.ingles.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chispa.ingles.data.db.AchievementEntity
import com.chispa.ingles.data.db.CertificateEntity
import com.chispa.ingles.data.db.ChispaDatabase
import com.chispa.ingles.data.db.DailyActivityEntity
import com.chispa.ingles.data.db.ExerciseStatEntity
import com.chispa.ingles.data.db.LessonProgressEntity
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.data.db.UserProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ida y vuelta del respaldo.
 *
 * Un respaldo que no se puede restaurar no es un respaldo, es un archivo. Y el
 * usuario solo lo descubre el día que cambia de teléfono, que es justo el día en
 * que ya no tiene el anterior. Así que se prueba el ciclo entero: guardar,
 * borrarlo todo, restaurar, y comprobar que está todo igual.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChispaDatabase
    private lateinit var manager: BackupManager

    @Before
    fun abrir() {
        db = Room.inMemoryDatabaseBuilder(context, ChispaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(context, db)
    }

    @After
    fun cerrar() = db.close()

    private suspend fun sembrar() {
        db.userProfileDao().upsert(
            UserProfileEntity(
                motive = "trabajo",
                studentName = "María Fernanda",
                studentSurname = "Ruiz Delgado",
                studentCity = "Guadalajara",
                avatarId = "michi",
                placementLevel = "B1",
                onboardingDone = true,
                placementDone = true,
                totalXp = 8450,
                currentStreak = 47,
                longestStreak = 63,
                hearts = 2,
                createdAt = 1_700_000_000_000L
            )
        )
        db.lessonProgressDao().insertAll(
            (1..12).map {
                LessonProgressEntity(
                    lessonId = "a1_u1_l$it", unitId = "a1_u1", trackId = "a1_core",
                    timesCompleted = 2, crown = 3, bestAccuracy = 90, xpEarned = 20
                )
            }
        )
        db.srsCardDao().insertAll(
            (1..40).map {
                SrsCardEntity(
                    cardKey = "palabra$it", en = "word$it", es = "palabra$it",
                    lessonId = "a1_u1_l1", unitId = "a1_u1", level = "A1",
                    strength = it % 6, dueAt = 1_700_000_000_000L + it, reps = it
                )
            }
        )
        db.dailyActivityDao().insertAll(
            (20_300L..20_330L).map { DailyActivityEntity(epochDay = it, xp = 30, goalMet = true) }
        )
        db.achievementDao().insertAll(
            listOf(AchievementEntity("racha_30", 1L), AchievementEntity("primera_leccion", 2L))
        )
        db.certificateDao().insertAll(
            listOf(
                CertificateEntity(
                    folio = "CH-A1-20680-B099", level = "A1",
                    studentName = "María Fernanda Ruiz Delgado", issuedAt = 1L,
                    lessonsCompleted = 15, accuracy = 94, totalXp = 1240
                )
            )
        )
        db.exerciseStatDao().insertAll(
            listOf(
                ExerciseStatEntity("multiple_choice", 200, 180),
                ExerciseStatEntity("listen_and_type", 60, 31)
            )
        )
    }

    @Test
    fun guardar_borrar_y_restaurar_deja_todo_igual() = runBlocking {
        sembrar()

        val antes = retrato()
        val archivo = manager.exportToCache()
        assertTrue("el archivo no se creó", archivo.exists() && archivo.length() > 100)

        // Se borra absolutamente todo, como en un teléfono nuevo.
        db.lessonProgressDao().clear()
        db.srsCardDao().clear()
        db.dailyActivityDao().clear()
        db.achievementDao().clear()
        db.certificateDao().clear()
        db.exerciseStatDao().clear()
        db.userProfileDao().clear()
        assertEquals(0, db.lessonProgressDao().all().size)

        // Se restaura leyendo el archivo tal cual, como haría el selector.
        val backup = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(BackupFile.serializer(), archivo.readText())
        manager.restore(backup).getOrThrow()

        assertEquals(antes, retrato())
    }

    @Test
    fun los_corazones_no_se_restauran() = runBlocking {
        sembrar()   // el perfil sembrado tiene 2 corazones
        val archivo = manager.exportToCache()
        val backup = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(BackupFile.serializer(), archivo.readText())

        db.userProfileDao().upsert(
            db.userProfileDao().get()!!.copy(hearts = UserProfileEntity.MAX_HEARTS)
        )
        manager.restore(backup).getOrThrow()

        // Restaurar no debe castigarte con los corazones que tenías al exportar.
        assertEquals(UserProfileEntity.MAX_HEARTS, db.userProfileDao().get()!!.hearts)
        // ...pero el resto del perfil sí vuelve.
        assertEquals(8450, db.userProfileDao().get()!!.totalXp)
    }

    @Test
    fun un_archivo_que_no_es_un_respaldo_se_rechaza_sin_tocar_nada() = runBlocking {
        sembrar()
        val antes = retrato()

        // Dentro de `respaldos/`: es la única carpeta de la caché que el
        // FileProvider expone. Ponerlo en la raíz falla, y está bien que falle.
        val dir = java.io.File(context.cacheDir, BackupManager.DIR).apply { mkdirs() }
        val basura = java.io.File(dir, "basura.json").apply {
            writeText("""{"cualquier":"cosa"}""")
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", basura
        )
        // No hace falta que `read` funcione con este Uri: lo que se comprueba es
        // que un fallo de lectura no deja la base a medias.
        runCatching { manager.read(uri) }

        assertEquals(antes, retrato())
    }

    /** Foto del estado completo, para comparar antes y después. */
    private suspend fun retrato(): String = buildString {
        val p = db.userProfileDao().get()
        append("perfil=${p?.totalXp}/${p?.currentStreak}/${p?.longestStreak}/")
        append("${p?.studentName}/${p?.studentSurname}/${p?.avatarId}/${p?.placementLevel}|")
        append("lecciones=${db.lessonProgressDao().all().sortedBy { it.lessonId }
            .joinToString { "${it.lessonId}:${it.crown}:${it.bestAccuracy}" }}|")
        append("tarjetas=${db.srsCardDao().all().sortedBy { it.cardKey }
            .joinToString { "${it.cardKey}:${it.strength}:${it.reps}" }}|")
        append("actividad=${db.dailyActivityDao().all().sortedBy { it.epochDay }
            .joinToString { "${it.epochDay}:${it.xp}" }}|")
        append("logros=${db.achievementDao().all().map { it.achievementId }.sorted()}|")
        append("certificados=${db.certificateDao().all().map { it.folio }.sorted()}|")
        append("stats=${db.exerciseStatDao().all().sortedBy { it.type }
            .joinToString { "${it.type}:${it.answered}:${it.correct}" }}")
    }
}
