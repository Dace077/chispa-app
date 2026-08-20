package com.chispa.ingles.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De esto depende que un simulacro de 115 minutos se pueda retomar sin perder
 * respuestas. Si falla, el alumno ve preguntas en blanco que sí contestó y no
 * hay manera de que se dé cuenta.
 */
class ExamProgressTest {

    @Test
    fun `lo que se guarda es exactamente lo que se recupera`() {
        val original = mapOf("l01" to 0, "s16" to 3, "r50" to 2)
        assertEquals(original, ExamProgress.decode(ExamProgress.encode(original)))
    }

    @Test
    fun `un examen entero de 140 respuestas sobrevive al viaje`() {
        val original = (1..140).associate { "q$it" to it % 4 }
        assertEquals(original, ExamProgress.decode(ExamProgress.encode(original)))
    }

    @Test
    fun `un mapa vacio va y vuelve vacio`() {
        assertTrue(ExamProgress.decode(ExamProgress.encode(emptyMap())).isEmpty())
    }

    @Test
    fun `una fila a medio escribir conserva las respuestas legibles`() {
        // Lo que quedaría si el proceso muere justo al escribir el último par.
        val leido = ExamProgress.decode("l01:2,l02:3,l0")
        assertEquals(mapOf("l01" to 2, "l02" to 3), leido)
    }

    @Test
    fun `se descarta la basura sin tirar el resto`() {
        val leido = ExamProgress.decode("l01:1,,sin_dos_puntos,l03:x,l04:2")
        assertEquals(mapOf("l01" to 1, "l04" to 2), leido)
    }

    @Test
    fun `una opcion fuera de rango no se acepta`() {
        // Un 7 solo puede venir de datos corrompidos: no hay quinta opción.
        assertEquals(mapOf("l02" to 3), ExamProgress.decode("l01:7,l02:3,l03:-1"))
    }

    @Test
    fun `un id con dos puntos dentro se lee bien`() {
        // Se parte por el ÚLTIMO separador, no por el primero.
        assertEquals(mapOf("parte:a:l01" to 2), ExamProgress.decode("parte:a:l01:2"))
    }

    @Test
    fun `los audios ya oidos van y vuelven`() {
        val ids = setOf("l01", "l31", "l43")
        assertEquals(ids, ExamProgress.decodeIds(ExamProgress.encodeIds(ids)))
    }

    @Test
    fun `una cadena vacia de audios no inventa ninguno`() {
        assertTrue(ExamProgress.decodeIds("").isEmpty())
    }
}
