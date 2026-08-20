package com.chispa.ingles.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.chispa.ingles.core.AppInfo
import com.chispa.ingles.core.Time
import com.chispa.ingles.data.db.ChispaDatabase
import com.chispa.ingles.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Guarda y restaura el progreso completo.
 *
 * Existe porque Chispa no tiene cuenta ni nube: si pierdes el móvil, pierdes la
 * racha de ocho meses. Esto es la única red de seguridad posible en una app que
 * ni siquiera puede conectarse.
 *
 * El archivo se escribe en la caché y sale de ahí por el mismo FileProvider que
 * los certificados. Para leerlo se usa el selector del sistema, que devuelve un
 * `Uri` con permiso puntual: la app nunca pide acceso al almacenamiento.
 */
class BackupManager(
    private val context: Context,
    private val db: ChispaDatabase
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // un respaldo viejo debe poder abrirse
        encodeDefaults = true
    }

    /* ------------------------------------------------------------------ */
    /*  Exportar                                                           */
    /* ------------------------------------------------------------------ */

    suspend fun exportToCache(): File = withContext(Dispatchers.IO) {
        val backup = BackupFile(
            appVersion = AppInfo.versionName(context),
            createdAt = Time.nowMillis(),
            profile = db.userProfileDao().get()?.toBackup(),
            lessons = db.lessonProgressDao().all().map { it.toBackup() },
            cards = db.srsCardDao().all().map { it.toBackup() },
            activity = db.dailyActivityDao().all().map { it.toBackup() },
            achievements = db.achievementDao().all().map { it.toBackup() },
            certificates = db.certificateDao().all().map { it.toBackup() },
            stats = db.exerciseStatDao().all().map { it.toBackup() },
            exams = db.examAttemptDao().all().map { it.toBackup() }
        )

        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val archivo = File(dir, nombreArchivo())
        archivo.writeText(json.encodeToString(BackupFile.serializer(), backup))
        archivo
    }

    private fun nombreArchivo(): String {
        val fecha = Time.nowDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return "chispa-progreso-$fecha.json"
    }

    /* ------------------------------------------------------------------ */
    /*  Leer sin aplicar                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Lee el archivo y comprueba que se puede usar, **sin tocar nada**.
     *
     * Se hace en dos pasos a propósito: restaurar borra el progreso actual, y
     * eso no puede pasar por sorpresa. Primero se lee, se le enseñan al usuario
     * las cifras que hay dentro, y solo si confirma se aplica.
     */
    suspend fun read(uri: Uri): Result<BackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            val texto = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("No se pudo abrir el archivo")

            val backup = json.decodeFromString(BackupFile.serializer(), texto)

            if (backup.formatVersion > BackupFile.FORMAT_VERSION) {
                error(
                    "Este respaldo lo hizo una versión más nueva de Chispa. " +
                        "Actualiza la app y vuelve a intentarlo."
                )
            }
            if (backup.profile == null && backup.lessons.isEmpty()) {
                error("El archivo no parece un respaldo de Chispa.")
            }
            backup
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Restaurar                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Reemplaza TODO el progreso por el del respaldo.
     *
     * Es reemplazo y no fusión a propósito: mezclar dos historiales de
     * repetición espaciada da fechas de repaso incoherentes y rachas que no
     * corresponden a días reales. "Este teléfono pasa a tener lo que había en
     * el otro" es una promesa que el usuario entiende; "se combinan" no.
     *
     * Va todo dentro de una transacción de Room: si falla a mitad, no queda una
     * base a medio restaurar.
     */
    suspend fun restore(backup: BackupFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // `withTransaction` y no `runInTransaction`: los DAO son suspend y
            // la versión de bloqueo no admite corrutinas dentro.
            db.withTransaction {
                db.lessonProgressDao().clear()
                db.srsCardDao().clear()
                db.dailyActivityDao().clear()
                db.achievementDao().clear()
                db.certificateDao().clear()
                db.exerciseStatDao().clear()
                db.examAttemptDao().clear()

                backup.profile?.let { p ->
                    val actual = db.userProfileDao().get()
                    db.userProfileDao().upsert(
                        p.toEntity(
                            hearts = actual?.hearts ?: UserProfileEntity.MAX_HEARTS,
                            heartsUpdatedAt = Time.nowMillis()
                        )
                    )
                }
                db.lessonProgressDao().insertAll(backup.lessons.map { it.toEntity() })
                db.srsCardDao().insertAll(backup.cards.map { it.toEntity() })
                db.dailyActivityDao().insertAll(backup.activity.map { it.toEntity() })
                db.achievementDao().insertAll(backup.achievements.map { it.toEntity() })
                db.certificateDao().insertAll(backup.certificates.map { it.toEntity() })
                db.exerciseStatDao().insertAll(backup.stats.map { it.toEntity() })
                db.examAttemptDao().insertAll(backup.exams.map { it.toEntity() })
            }
        }
    }

    companion object {
        const val DIR = "respaldos"
        const val MIME = "application/json"
    }
}
