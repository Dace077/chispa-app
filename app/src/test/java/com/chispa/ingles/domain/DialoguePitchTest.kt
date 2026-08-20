package com.chispa.ingles.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialoguePitchTest {

    @Test
    fun `la mujer suena mas aguda que el hombre`() {
        val tonos = DialoguePitch.forSpeakers(listOf("Man", "Woman"))
        assertTrue(tonos.getValue("Woman") > tonos.getValue("Man"))
    }

    @Test
    fun `el orden en el guion no cambia quien suena grave`() {
        val a = DialoguePitch.forSpeakers(listOf("Man", "Woman"))
        val b = DialoguePitch.forSpeakers(listOf("Woman", "Man"))
        assertEquals(a, b)
    }

    @Test
    fun `woman contiene man y aun asi suena aguda`() {
        // Si se comprobara «man» primero, toda mujer sonaría grave.
        val tonos = DialoguePitch.forSpeakers(listOf("Woman", "Man"))
        assertTrue(tonos.getValue("Woman") > DialoguePitch.NEUTRO)
    }

    @Test
    fun `dos roles sin genero suenan distinto`() {
        val tonos = DialoguePitch.forSpeakers(listOf("Student", "Librarian"))
        assertNotEquals(tonos.getValue("Student"), tonos.getValue("Librarian"))
    }

    @Test
    fun `un rol sin genero no le roba el tono al que si lo declara`() {
        val tonos = DialoguePitch.forSpeakers(listOf("Woman", "Technician"))
        assertNotEquals(tonos.getValue("Woman"), tonos.getValue("Technician"))
    }

    @Test
    fun `una charla de un solo ponente se lee con voz normal`() {
        val tonos = DialoguePitch.forSpeakers(listOf("Professor", "Professor", "Professor"))
        assertEquals(DialoguePitch.NEUTRO, tonos.getValue("Professor"), 0.001f)
    }

    @Test
    fun `tres personajes reciben tres tonos distintos`() {
        val tonos = DialoguePitch.forSpeakers(listOf("Man", "Woman", "Narrator"))
        assertEquals(3, tonos.values.toSet().size)
    }

    @Test
    fun `los turnos repetidos no crean personajes nuevos`() {
        val tonos = DialoguePitch.forSpeakers(
            listOf("Student", "Adviser", "Student", "Adviser", "Student")
        )
        assertEquals(2, tonos.size)
    }

    @Test
    fun `un guion vacio no revienta`() {
        assertTrue(DialoguePitch.forSpeakers(emptyList()).isEmpty())
    }

    @Test
    fun `los nombres en blanco se ignoran`() {
        val tonos = DialoguePitch.forSpeakers(listOf("Man", "  ", "Woman"))
        assertEquals(2, tonos.size)
    }
}
