package com.chispa.ingles.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de la base de datos.
 *
 * Hasta la versión 1 la app vivía con `fallbackToDestructiveMigration`, que ante
 * cualquier cambio de esquema borra la base entera. Con una app que solo guarda
 * progreso local y ninguna copia en la nube, eso significa que el usuario pierde
 * su racha, su XP y sus meses de trabajo sin que nadie se lo pregunte, y encima
 * en silencio: la app abre tan contenta y vacía.
 *
 * A partir de aquí las migraciones se escriben a mano. Es la única forma de
 * añadir cosas —el nombre del alumno, los certificados, las estadísticas— sin
 * cobrárselo a quien ya llevaba tiempo usándola.
 */

/**
 * v1 → v2. Todo lo que necesitan el certificado, los avatares, las estadísticas
 * por tipo de ejercicio y los simulacros de examen.
 *
 * Se hace en una sola migración a propósito, aunque las funciones lleguen por
 * separado: cada migración extra es otra oportunidad de romperle la base a
 * alguien, y estas columnas ya se sabe que hacen falta.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // --- Datos del alumno, para poder emitir un certificado a su nombre ---
        // SQLite exige DEFAULT al añadir columnas NOT NULL a una tabla con filas.
        db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `studentName` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `studentSurname` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `studentCity` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `studentRegisteredAt` INTEGER NOT NULL DEFAULT 0")

        // --- Avatar elegido. Quien ya usaba la app se queda con Chispa. ---
        db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `avatarId` TEXT NOT NULL DEFAULT 'chispa'")

        // --- Certificados emitidos ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `certificate` (
                `folio` TEXT NOT NULL,
                `level` TEXT NOT NULL,
                `studentName` TEXT NOT NULL,
                `issuedAt` INTEGER NOT NULL,
                `lessonsCompleted` INTEGER NOT NULL,
                `accuracy` INTEGER NOT NULL,
                `totalXp` INTEGER NOT NULL,
                PRIMARY KEY(`folio`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_certificate_level` ON `certificate` (`level`)")

        // --- Aciertos y fallos por tipo de ejercicio ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercise_stat` (
                `type` TEXT NOT NULL,
                `answered` INTEGER NOT NULL,
                `correct` INTEGER NOT NULL,
                `lastAnsweredAt` INTEGER NOT NULL,
                PRIMARY KEY(`type`)
            )
            """.trimIndent()
        )

        // --- Intentos de simulacro de certificación ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exam_attempt` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `examId` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `finishedAt` INTEGER NOT NULL,
                `listeningRaw` INTEGER NOT NULL,
                `structureRaw` INTEGER NOT NULL,
                `readingRaw` INTEGER NOT NULL,
                `scaledScore` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_attempt_examId` ON `exam_attempt` (`examId`)")
    }
}

/**
 * v2 → v3. El intento de simulacro guarda ahora lo que el alumno contestó y
 * dónde se quedó.
 *
 * Antes solo se escribía la fila al terminar, con los aciertos por sección. Eso
 * dejaba dos agujeros: no había forma de revisar los fallos después (que es lo
 * único que enseña de un simulacro) y un examen de 115 minutos se perdía entero
 * si Android mataba el proceso.
 *
 * Los intentos que ya existieran se quedan con los campos vacíos: no se puede
 * inventar lo que alguien contestó. Aparecerán como terminados y sin revisión.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exam_attempt` ADD COLUMN `answers` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `exam_attempt` ADD COLUMN `played` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `exam_attempt` ADD COLUMN `sectionIndex` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `exam_attempt` ADD COLUMN `questionIndex` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `exam_attempt` ADD COLUMN `secondsLeft` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Todas las migraciones, en orden. Se pasan tal cual a Room. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
