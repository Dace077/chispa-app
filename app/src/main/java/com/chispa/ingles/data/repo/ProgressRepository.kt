package com.chispa.ingles.data.repo

import com.chispa.ingles.core.Time
import com.chispa.ingles.data.content.CefrLevel
import com.chispa.ingles.data.content.Curriculum
import com.chispa.ingles.data.content.Exercise
import com.chispa.ingles.data.content.Lesson
import com.chispa.ingles.data.db.AchievementEntity
import com.chispa.ingles.data.db.CertificateEntity
import com.chispa.ingles.data.db.ChispaDatabase
import com.chispa.ingles.data.db.DailyActivityEntity
import com.chispa.ingles.data.db.ExerciseStatEntity
import com.chispa.ingles.data.db.LessonProgressEntity
import com.chispa.ingles.data.db.SrsCardEntity
import com.chispa.ingles.data.db.UserProfileEntity
import com.chispa.ingles.data.prefs.SettingsStore
import com.chispa.ingles.domain.Achievement
import com.chispa.ingles.domain.Achievements
import com.chispa.ingles.domain.CertificateRules
import com.chispa.ingles.domain.LevelCompletion
import com.chispa.ingles.domain.PlacementLadder
import com.chispa.ingles.domain.PlayerStats
import com.chispa.ingles.domain.Rank
import com.chispa.ingles.domain.Ranks
import com.chispa.ingles.domain.Srs
import com.chispa.ingles.domain.Xp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Único punto de escritura del progreso del usuario.
 *
 * Todas las mutaciones pasan por un mutex porque varias pueden dispararse casi
 * a la vez (terminar la lección, actualizar el SRS, evaluar logros) y estamos
 * haciendo lectura-modificación-escritura sobre la misma fila de perfil.
 */
class ProgressRepository(
    db: ChispaDatabase,
    private val settings: SettingsStore
) {

    private val profileDao = db.userProfileDao()
    private val progressDao = db.lessonProgressDao()
    private val srsDao = db.srsCardDao()
    private val activityDao = db.dailyActivityDao()
    private val achievementDao = db.achievementDao()
    private val certificateDao = db.certificateDao()
    private val exerciseStatDao = db.exerciseStatDao()

    private val writeLock = Mutex()

    /* ------------------------------------------------------------------ */
    /*  Observables                                                        */
    /* ------------------------------------------------------------------ */

    val profile: Flow<UserProfileEntity> = profileDao.observe()
        .map { it ?: UserProfileEntity(createdAt = Time.nowMillis()) }

    val lessonProgress: Flow<Map<String, LessonProgressEntity>> = progressDao.observeAll()
        .map { list -> list.associateBy { it.lessonId } }

    val achievements: Flow<List<AchievementEntity>> = achievementDao.observeAll()

    val vocabSeenCount: Flow<Int> = srsDao.observeSeenCount()

    val vocabMasteredCount: Flow<Int> = srsDao.observeMasteredCount(minStrength = 4)

    fun activitySince(epochDay: Long): Flow<List<DailyActivityEntity>> =
        activityDao.observeSince(epochDay)

    fun todayActivity(): Flow<DailyActivityEntity> =
        activityDao.observe(Time.todayEpochDay())
            .map { it ?: DailyActivityEntity(epochDay = Time.todayEpochDay()) }

    /** XP conseguida hoy. Lectura puntual, para el widget y las notificaciones. */
    suspend fun xpToday(): Int = activityDao.get(Time.todayEpochDay())?.xp ?: 0

    /* ------------------------------------------------------------------ */
    /*  Ciclo de vida                                                      */
    /* ------------------------------------------------------------------ */

    suspend fun ensureProfile(): UserProfileEntity = writeLock.withLock { ensureProfileLocked() }

    private suspend fun ensureProfileLocked(): UserProfileEntity {
        profileDao.get()?.let { return it }
        val fresh = UserProfileEntity(
            createdAt = Time.nowMillis(),
            heartsUpdatedAt = Time.nowMillis(),
            lastActiveDay = Time.todayEpochDay()
        )
        profileDao.upsert(fresh)
        return fresh
    }

    /**
     * Se llama al abrir la app. Resuelve dos cosas que no pueden esperar:
     * si la racha sigue viva y si el usuario vuelve tras un parón.
     */
    suspend fun onAppOpen(): OpenOutcome = writeLock.withLock {
        val profile = ensureProfileLocked()
        val today = Time.todayEpochDay()
        val daysAway = if (profile.lastActiveDay == 0L) 0 else (today - profile.lastActiveDay).toInt()

        var updated = profile.copy(lastActiveDay = today)
        var streakLost = false
        var freezeUsed = false

        if (profile.lastGoalDay > 0L) {
            val gap = today - profile.lastGoalDay
            when {
                // Hoy o ayer: la racha sigue viva sin hacer nada.
                gap <= 1L -> Unit
                // Se saltó exactamente un día y tiene comodín: lo gastamos.
                gap == 2L && profile.streakFreezes > 0 -> {
                    updated = updated.copy(
                        streakFreezes = profile.streakFreezes - 1,
                        lastGoalDay = today - 1
                    )
                    freezeUsed = true
                }
                else -> {
                    if (profile.currentStreak > 0) streakLost = true
                    updated = updated.copy(currentStreak = 0)
                }
            }
        }

        profileDao.upsert(updated)
        settings.setLastOpenedDay(today)

        if (daysAway >= 7) settings.addSpecialFlag("comeback")
        val hour = Time.nowDateTime().hour
        if (hour < 7) settings.addSpecialFlag("early_bird")
        if (hour >= 0 && hour < 4) settings.addSpecialFlag("night_owl")

        OpenOutcome(
            profile = updated,
            daysAway = daysAway,
            streakLost = streakLost,
            freezeUsed = freezeUsed
        )
    }

    /* ------------------------------------------------------------------ */
    /*  Onboarding / ajustes de perfil                                     */
    /* ------------------------------------------------------------------ */

    suspend fun completeOnboarding(motive: String, dailyGoalXp: Int) = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(
            profile.copy(
                motive = motive,
                dailyGoalXp = dailyGoalXp,
                onboardingDone = true
            )
        )
    }

    suspend fun completePlacement(level: CefrLevel) = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(profile.copy(placementLevel = level.label, placementDone = true))
    }

    suspend fun skipPlacement() = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(profile.copy(placementDone = true))
    }

    /**
     * Repite el test de nivel sin tocar el progreso.
     *
     * **El nivel solo puede subir.** No es un capricho: `UnlockRules` abre los
     * niveles por debajo del asignado, así que bajar el nivel volvería a cerrar
     * contenido que el usuario ya tenía abierto —y que a lo mejor llevaba
     * semanas usando— sin avisarle de nada. Quien repite el test quiere saber
     * si ha mejorado, no perder terreno.
     *
     * XP, racha, lecciones, tarjetas y logros se quedan exactamente como están:
     * esto solo mueve la marca de por dónde seguir.
     *
     * @return el nivel que queda finalmente asignado.
     */
    suspend fun retakePlacement(level: CefrLevel): CefrLevel = writeLock.withLock {
        val profile = ensureProfileLocked()
        val actual = CefrLevel.from(profile.placementLevel)
        val definitivo = PlacementLadder.afterRetake(actual, level)

        if (definitivo != actual) {
            profileDao.upsert(profile.copy(placementLevel = definitivo.label))
        }
        definitivo
    }

    suspend fun setDailyGoal(xp: Int) = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(profile.copy(dailyGoalXp = xp))
    }

    /**
     * Guarda los datos del alumno: los que se imprimirán en el certificado.
     *
     * `studentRegisteredAt` solo se sella la primera vez. Es la fecha de
     * inscripción que aparece en la constancia, y corregir una falta de
     * ortografía en el apellido tres meses después no debería reescribir el día
     * en que la persona empezó el curso.
     */
    suspend fun saveStudentData(
        name: String,
        surname: String,
        city: String
    ) = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(
            profile.copy(
                studentName = name.trim(),
                studentSurname = surname.trim(),
                studentCity = city.trim(),
                studentRegisteredAt = if (profile.studentRegisteredAt == 0L) {
                    Time.nowMillis()
                } else {
                    profile.studentRegisteredAt
                }
            )
        )
    }

    suspend fun setAvatar(avatarId: String) = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(profile.copy(avatarId = avatarId))
    }

    /* ------------------------------------------------------------------ */
    /*  Certificados                                                       */
    /* ------------------------------------------------------------------ */

    val certificates: Flow<List<CertificateEntity>> = certificateDao.observeAll()

    suspend fun certificateFor(level: CefrLevel): CertificateEntity? =
        certificateDao.forLevel(level.label)

    /**
     * Emite el certificado de un nivel, o devuelve el que ya existía.
     *
     * Es idempotente a propósito: entrar dos veces en la pantalla no debe
     * generar dos folios distintos para el mismo logro. El primero que se emitió
     * es el bueno, con su fecha y su nombre de aquel momento.
     */
    suspend fun issueCertificate(
        level: CefrLevel,
        completion: LevelCompletion
    ): CertificateEntity? = writeLock.withLock {
        certificateDao.forLevel(level.label)?.let { return@withLock it }

        val profile = ensureProfileLocked()
        if (!profile.canReceiveCertificate) return@withLock null

        val ahora = Time.nowMillis()
        val certificado = CertificateEntity(
            folio = CertificateRules.folio(level, profile.fullName, Time.todayEpochDay()),
            level = level.label,
            studentName = profile.fullName,
            issuedAt = ahora,
            lessonsCompleted = completion.completedLessons,
            accuracy = completion.accuracy,
            totalXp = completion.xpEarned
        )
        certificateDao.issue(certificado)
        certificateDao.forLevel(level.label) ?: certificado
    }

    /* ------------------------------------------------------------------ */
    /*  Estadísticas por tipo de ejercicio                                 */
    /* ------------------------------------------------------------------ */

    val exerciseStats: Flow<List<ExerciseStatEntity>> = exerciseStatDao.observeAll()

    /**
     * Suma una respuesta al contador de su tipo.
     *
     * No pasa por el mutex de escritura: es un contador independiente, no toca
     * la fila de perfil y el DAO ya lo resuelve en una transacción. Meterlo en
     * la cola general solo añadiría espera en mitad de un ejercicio.
     */
    suspend fun recordExerciseStat(type: String, correct: Boolean) {
        if (type == "info") return
        exerciseStatDao.record(type, correct, Time.nowMillis())
    }

    /* ------------------------------------------------------------------ */
    /*  Corazones                                                          */
    /* ------------------------------------------------------------------ */

    /** Aplica la regeneración pendiente y devuelve los corazones reales de ahora. */
    suspend fun refreshHearts(): Int = writeLock.withLock {
        val profile = ensureProfileLocked()
        val (hearts, updatedAt) = regenerate(profile)
        if (hearts != profile.hearts || updatedAt != profile.heartsUpdatedAt) {
            profileDao.upsert(profile.copy(hearts = hearts, heartsUpdatedAt = updatedAt))
        }
        hearts
    }

    private fun regenerate(profile: UserProfileEntity): Pair<Int, Long> {
        if (profile.hearts >= UserProfileEntity.MAX_HEARTS) {
            return UserProfileEntity.MAX_HEARTS to Time.nowMillis()
        }
        val now = Time.nowMillis()
        val base = if (profile.heartsUpdatedAt == 0L) now else profile.heartsUpdatedAt
        val recovered = ((now - base) / UserProfileEntity.HEART_REFILL_MILLIS).toInt().coerceAtLeast(0)
        if (recovered == 0) return profile.hearts to base

        val hearts = (profile.hearts + recovered).coerceAtMost(UserProfileEntity.MAX_HEARTS)
        val updatedAt = if (hearts >= UserProfileEntity.MAX_HEARTS) {
            now
        } else {
            base + recovered * UserProfileEntity.HEART_REFILL_MILLIS
        }
        return hearts to updatedAt
    }

    suspend fun consumeHeart(): Int = writeLock.withLock {
        val profile = ensureProfileLocked()
        val (current, updatedAt) = regenerate(profile)
        val remaining = (current - 1).coerceAtLeast(0)
        // El reloj de regeneración arranca justo al perder un corazón estando lleno.
        val newUpdatedAt = if (current >= UserProfileEntity.MAX_HEARTS) Time.nowMillis() else updatedAt
        profileDao.upsert(profile.copy(hearts = remaining, heartsUpdatedAt = newUpdatedAt))
        remaining
    }

    /** Recompensa por terminar un repaso rápido: corazones al máximo. */
    suspend fun restoreHearts() = writeLock.withLock {
        val profile = ensureProfileLocked()
        profileDao.upsert(
            profile.copy(
                hearts = UserProfileEntity.MAX_HEARTS,
                heartsUpdatedAt = Time.nowMillis()
            )
        )
    }

    suspend fun millisUntilNextHeart(): Long {
        val profile = profileDao.get() ?: return 0L
        if (profile.hearts >= UserProfileEntity.MAX_HEARTS) return 0L
        val base = if (profile.heartsUpdatedAt == 0L) Time.nowMillis() else profile.heartsUpdatedAt
        val elapsed = (Time.nowMillis() - base) % UserProfileEntity.HEART_REFILL_MILLIS
        return (UserProfileEntity.HEART_REFILL_MILLIS - elapsed).coerceAtLeast(0L)
    }

    /* ------------------------------------------------------------------ */
    /*  Repetición espaciada                                               */
    /* ------------------------------------------------------------------ */

    /** Registra en el SRS todo el vocabulario de una lección que se acaba de abrir. */
    suspend fun seedVocab(lesson: Lesson) {
        if (lesson.vocab.isEmpty()) return
        val now = Time.nowMillis()
        srsDao.insertIgnore(
            lesson.vocab.map { item ->
                SrsCardEntity(
                    cardKey = item.srsKey,
                    en = item.en,
                    es = item.es,
                    lessonId = lesson.id,
                    unitId = lesson.unitId,
                    level = lesson.level.label,
                    strength = 0,
                    dueAt = now,
                    reps = 0
                )
            }
        )
    }

    /**
     * Aplica una respuesta al SRS. Si la clave no existía (por ejemplo una frase
     * que solo aparece como ejercicio) se crea la tarjeta sobre la marcha.
     */
    suspend fun recordAnswer(
        cardKey: String,
        correct: Boolean,
        en: String,
        es: String,
        lesson: Lesson?
    ) {
        val key = cardKey.trim().lowercase()
        if (key.isEmpty()) return
        val now = Time.nowMillis()
        val existing = srsDao.get(key) ?: SrsCardEntity(
            cardKey = key,
            en = en.trim(),
            es = es.trim(),
            lessonId = lesson?.id.orEmpty(),
            unitId = lesson?.unitId.orEmpty(),
            level = lesson?.level?.label ?: CefrLevel.A1.label,
            dueAt = now
        )
        srsDao.upsert(Srs.review(existing, correct, now))
    }

    /**
     * Saca del repaso las tarjetas que nunca debieron entrar.
     *
     * Hasta la 1.7.0 cualquier ejercicio de opción múltiple creaba una tarjeta,
     * incluidos los de gramática. En esos el enunciado es una instrucción
     * ("¿Cuál está bien escrito?"), no la traducción de la respuesta, así que el
     * repaso acababa preguntando "¿Qué significa? two yellow bananas" y dando por
     * buena la instrucción de otro ejercicio.
     *
     * Se borran por clave exacta, calculada desde el propio contenido, y se
     * respeta cualquier clave que además sea vocabulario declarado, que es de
     * donde salen las tarjetas buenas.
     *
     * Es idempotente: correrla dos veces no hace nada la segunda.
     */
    suspend fun purgeNonVocabCards(curriculum: Curriculum): Int {
        val legitimas = curriculum.vocabIndex.keys
        val sospechosas = curriculum.allLessons
            .flatMap { it.exercises }
            .filterIsInstance<Exercise.MultipleChoice>()
            .map { it.srsKey }
            .distinct()
            .filterNot { it in legitimas }

        // SQLite limita los parámetros de una consulta; se va por tandas.
        val borradas = sospechosas.chunked(400).sumOf { srsDao.deleteByKeys(it) }

        // Y las que sí son vocabulario pero guardaron el texto equivocado se
        // reparan en vez de borrarse, para no tirar el progreso del usuario.
        var reparadas = 0
        curriculum.vocabIndex.keys.chunked(400).forEach { tanda ->
            srsDao.getAll(tanda).forEach { tarjeta ->
                val bueno = curriculum.vocabIndex[tarjeta.cardKey] ?: return@forEach
                if (tarjeta.en != bueno.en || tarjeta.es != bueno.es) {
                    srsDao.retext(tarjeta.cardKey, bueno.en, bueno.es)
                    reparadas++
                }
            }
        }
        if (reparadas > 0) {
            android.util.Log.i("Chispa", "Repaso reparado: $reparadas tarjetas con el texto cambiado")
        }
        return borradas
    }

    suspend fun dueCards(limit: Int): List<SrsCardEntity> =
        srsDao.dueCards(Time.nowMillis(), limit)

    suspend fun dueCount(): Int = srsDao.dueCount(Time.nowMillis())

    suspend fun hardestCards(limit: Int): List<SrsCardEntity> = srsDao.hardestCards(limit)

    /** Las menos consolidadas, aunque todavía no se haya fallado ninguna. */
    suspend fun weakestCards(limit: Int): List<SrsCardEntity> = srsDao.weakestCards(limit)

    fun observeVocabulary(limit: Int = 500): Flow<List<SrsCardEntity>> = srsDao.observeAll(limit)

    /* ------------------------------------------------------------------ */
    /*  Cierre de sesión de práctica                                       */
    /* ------------------------------------------------------------------ */

    /**
     * Registra el resultado de una lección terminada: XP, racha, actividad
     * diaria, corona de la lección y logros nuevos.
     */
    suspend fun completeLesson(
        lesson: Lesson,
        correct: Int,
        totalGraded: Int,
        speakingAnswered: Int
    ): SessionOutcome {
        if (speakingAnswered > 0) settings.incrementSpeaking(speakingAnswered)
        val perfect = totalGraded > 0 && correct == totalGraded
        if (perfect) settings.incrementPerfectLessons()

        lesson.trackId.let { trackId ->
            when {
                trackId.contains("idiom") -> settings.addSpecialFlag("idiom_hunter")
                trackId.contains("business") -> settings.addSpecialFlag("business_ready")
                trackId.contains("travel") -> settings.addSpecialFlag("traveler")
                trackId.contains("stor") -> settings.addSpecialFlag("first_story")
            }
        }

        val xp = Xp.forLesson(correct, totalGraded)

        return writeLock.withLock {
            val existing = progressDao.get(lesson.id)
            val accuracy = if (totalGraded > 0) correct * 100 / totalGraded else 100
            val timesCompleted = (existing?.timesCompleted ?: 0) + 1
            progressDao.upsert(
                LessonProgressEntity(
                    lessonId = lesson.id,
                    unitId = lesson.unitId,
                    trackId = lesson.trackId,
                    timesCompleted = timesCompleted,
                    crown = timesCompleted.coerceAtMost(MAX_CROWN),
                    bestAccuracy = maxOf(existing?.bestAccuracy ?: 0, accuracy),
                    xpEarned = (existing?.xpEarned ?: 0) + xp,
                    lastCompletedAt = Time.nowMillis()
                )
            )
            applySessionLocked(
                xp = xp,
                lessonsCompleted = 1,
                exercisesAnswered = totalGraded,
                correctAnswers = correct
            ).copy(crown = timesCompleted.coerceAtMost(MAX_CROWN), perfect = perfect)
        }
    }

    /** Cierre de una sesión de repaso espaciado. */
    suspend fun completeReview(correct: Int, total: Int): SessionOutcome {
        settings.incrementReviewSessions()
        val xp = correct * Xp.PER_REVIEW_CORRECT + if (total > 0) Xp.REVIEW_COMPLETE else 0
        return writeLock.withLock {
            applySessionLocked(
                xp = xp,
                lessonsCompleted = 0,
                exercisesAnswered = total,
                correctAnswers = correct
            )
        }
    }

    /** Cierre de una sesión libre de pronunciación. */
    suspend fun completeSpeakingSession(correct: Int, total: Int): SessionOutcome {
        settings.incrementSpeaking(total)
        val xp = correct * Xp.PER_REVIEW_CORRECT
        return writeLock.withLock {
            applySessionLocked(
                xp = xp,
                lessonsCompleted = 0,
                exercisesAnswered = total,
                correctAnswers = correct
            )
        }
    }

    /**
     * Núcleo compartido: suma XP, actualiza el día, decide si la meta se cumplió
     * y si la racha crece, y evalúa logros. Debe llamarse con [writeLock] tomado.
     */
    private suspend fun applySessionLocked(
        xp: Int,
        lessonsCompleted: Int,
        exercisesAnswered: Int,
        correctAnswers: Int
    ): SessionOutcome {
        val profile = ensureProfileLocked()
        val today = Time.todayEpochDay()
        val previousActivity = activityDao.get(today) ?: DailyActivityEntity(epochDay = today)

        val newDayXp = previousActivity.xp + xp
        val goalMet = newDayXp >= profile.dailyGoalXp
        val goalJustMet = goalMet && !previousActivity.goalMet

        activityDao.upsert(
            previousActivity.copy(
                xp = newDayXp,
                lessonsCompleted = previousActivity.lessonsCompleted + lessonsCompleted,
                exercisesAnswered = previousActivity.exercisesAnswered + exercisesAnswered,
                correctAnswers = previousActivity.correctAnswers + correctAnswers,
                goalMet = goalMet
            )
        )

        var updated = profile.copy(
            totalXp = profile.totalXp + xp,
            lastActiveDay = today
        )

        var streakIncreased = false
        if (goalJustMet) {
            val gap = if (profile.lastGoalDay == 0L) Long.MAX_VALUE else today - profile.lastGoalDay
            val newStreak = when {
                profile.lastGoalDay == today -> profile.currentStreak
                gap == 1L -> profile.currentStreak + 1
                else -> 1
            }
            streakIncreased = newStreak > profile.currentStreak
            updated = updated.copy(
                currentStreak = newStreak,
                longestStreak = maxOf(profile.longestStreak, newStreak),
                lastGoalDay = today
            )

            // Un comodín por semana practicada, con tope de 2 acumulados.
            val week = Time.weekId()
            if (updated.lastFreezeWeek != week && updated.streakFreezes < MAX_FREEZES) {
                updated = updated.copy(
                    streakFreezes = updated.streakFreezes + 1,
                    lastFreezeWeek = week
                )
            }
        }

        val previousRank = Ranks.current(profile.totalXp)
        val newRank = Ranks.current(updated.totalXp)
        profileDao.upsert(updated)

        val unlockedAchievements = evaluateAchievementsLocked(updated)

        return SessionOutcome(
            xpEarned = xp,
            totalXp = updated.totalXp,
            dayXp = newDayXp,
            dailyGoalXp = updated.dailyGoalXp,
            streak = updated.currentStreak,
            streakIncreased = streakIncreased,
            goalMet = goalMet,
            goalJustMet = goalJustMet,
            newAchievements = unlockedAchievements,
            rankUp = if (newRank.name != previousRank.name) newRank else null,
            streakFreezes = updated.streakFreezes,
            crown = 0,
            perfect = false
        )
    }

    private suspend fun evaluateAchievementsLocked(profile: UserProfileEntity): List<Achievement> {
        val prefs = settings.current()
        val stats = PlayerStats(
            lessonsCompleted = progressDao.completedCount(),
            currentStreak = profile.currentStreak,
            totalXp = profile.totalXp,
            vocabLearned = srsDao.seenCount(),
            speakingExercises = prefs.speakingExercises,
            reviewSessions = prefs.reviewSessions,
            perfectLessons = prefs.perfectLessons,
            specialFlags = prefs.specialFlags
        )
        val shouldBeUnlocked = Achievements.evaluate(stats)
        val already = achievementDao.unlockedIds().toSet()
        val fresh = shouldBeUnlocked - already
        if (fresh.isEmpty()) return emptyList()

        val now = Time.nowMillis()
        achievementDao.unlockAll(fresh.map { AchievementEntity(it, now) })
        return fresh.mapNotNull { Achievements.byId(it) }
    }

    /* ------------------------------------------------------------------ */
    /*  Estadísticas para el perfil                                        */
    /* ------------------------------------------------------------------ */

    suspend fun weeklyXp(weeksAgo: Int = 0): Int {
        val monday = Time.startOfWeek().minusWeeks(weeksAgo.toLong())
        return activityDao.xpBetween(monday.toEpochDay(), monday.plusDays(6).toEpochDay())
    }

    /* ------------------------------------------------------------------ */
    /*  Reinicio                                                           */
    /* ------------------------------------------------------------------ */

    suspend fun resetEverything() = writeLock.withLock {
        progressDao.clear()
        srsDao.clear()
        activityDao.clear()
        achievementDao.clear()
        profileDao.clear()
        settings.resetStats()
        profileDao.upsert(
            UserProfileEntity(
                createdAt = Time.nowMillis(),
                heartsUpdatedAt = Time.nowMillis(),
                lastActiveDay = Time.todayEpochDay()
            )
        )
    }

    companion object {
        const val MAX_CROWN = 5
        const val MAX_FREEZES = 2
    }
}

data class OpenOutcome(
    val profile: UserProfileEntity,
    val daysAway: Int,
    val streakLost: Boolean,
    val freezeUsed: Boolean
)

data class SessionOutcome(
    val xpEarned: Int,
    val totalXp: Int,
    val dayXp: Int,
    val dailyGoalXp: Int,
    val streak: Int,
    val streakIncreased: Boolean,
    val goalMet: Boolean,
    val goalJustMet: Boolean,
    val newAchievements: List<Achievement>,
    val rankUp: Rank?,
    val streakFreezes: Int,
    val crown: Int,
    val perfect: Boolean
)
