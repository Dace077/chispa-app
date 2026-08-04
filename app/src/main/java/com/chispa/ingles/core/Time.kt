package com.chispa.ingles.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Todo el manejo de fechas de la app pasa por aquí.
 *
 * Motivo: la racha depende de "días de calendario en la zona horaria del
 * usuario", no de bloques de 24 horas. Centralizarlo evita que media app use
 * `System.currentTimeMillis() / 86400000` y la otra media use `LocalDate`.
 */
object Time {

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun today(): LocalDate = LocalDate.now(zone())

    fun todayEpochDay(): Long = today().toEpochDay()

    fun epochDayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toEpochDay()

    fun dateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun nowDateTime(): LocalDateTime = LocalDateTime.now(zone())

    /** Identificador de semana estable, tipo "2026-W31". Usado para los comodines. */
    fun weekId(date: LocalDate = today()): String {
        val fields = WeekFields.ISO
        val week = date.get(fields.weekOfWeekBasedYear())
        val year = date.get(fields.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }

    /** Lunes de la semana que contiene [date]. */
    fun startOfWeek(date: LocalDate = today()): LocalDate =
        date.minusDays(((date.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())

    /** Milisegundos hasta la próxima ocurrencia de [hour]:[minute]. */
    fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now(zone())
        var target = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(zone()).toInstant().toEpochMilli() - now.atZone(zone()).toInstant().toEpochMilli()
    }

    /** Milisegundos que faltan para que termine el día de hoy. */
    fun millisUntilEndOfDay(): Long {
        val now = LocalDateTime.now(zone())
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay()
        return endOfDay.atZone(zone()).toInstant().toEpochMilli() -
            now.atZone(zone()).toInstant().toEpochMilli()
    }

    private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")

    fun shortWeekdayLabel(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    fun monthName(date: LocalDate): String = MONTHS[date.monthValue - 1]

    fun formatDay(date: LocalDate): String = "${date.dayOfMonth} de ${monthName(date)}"
}
