package com.chispa.ingles.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El inglés británico y el americano son los dos correctos, y la propia app lo
 * enseña en A1. Antes "centre" se marcaba como fallo frente a "center" y
 * "colour" pasaba pero avisando de una errata inexistente.
 */
class AnswerCheckerSpellingTest {

    private fun aceptadaSinAviso(escrito: String, esperado: String) {
        val r = AnswerChecker.check(escrito, esperado)
        assertTrue("'$escrito' deberia valer para '$esperado', dio $r", r is AnswerChecker.Result.Correct)
    }

    @Test
    fun `centre y center valen lo mismo`() {
        // Este era el peor caso: dos letras de diferencia con presupuesto de una,
        // asi que se marcaba directamente como incorrecta.
        aceptadaSinAviso("the city centre", "the city center")
        aceptadaSinAviso("the city center", "the city center")
    }

    @Test
    fun `theatre y theater valen lo mismo`() {
        aceptadaSinAviso("I go to the theatre", "I go to the theater")
    }

    @Test
    fun `colour y color no son una errata`() {
        aceptadaSinAviso("My favourite colour is green", "My favorite color is green")
    }

    @Test
    fun `las formas en -ise valen igual que las de -ize`() {
        aceptadaSinAviso("I did not realise", "I did not realize")
        aceptadaSinAviso("He should apologise", "He should apologize")
    }

    @Test
    fun `las dobles consonantes britanicas valen`() {
        aceptadaSinAviso("We are travelling today", "We are traveling today")
        aceptadaSinAviso("The train was cancelled", "The train was canceled")
    }

    @Test
    fun `los pasados irregulares que conviven valen los dos`() {
        aceptadaSinAviso("I learnt a lot", "I learned a lot")
        aceptadaSinAviso("She spelt it wrong", "She spelled it wrong")
    }

    @Test
    fun `funciona en los dos sentidos`() {
        aceptadaSinAviso("neighbour", "neighbor")
        aceptadaSinAviso("neighbor", "neighbour")
    }

    /* ----------------------- Lo que NO debe romperse ---------------------- */

    @Test
    fun `no se toca ninguna palabra que solo se parezca`() {
        // Una regla general "-our a -or" convertiria estas en otra cosa.
        listOf("four", "hour", "your", "our", "tour", "pour", "sour", "flour").forEach {
            assertEquals(it, AnswerChecker.normalize(it))
        }
        // Y una regla "-re a -er" romperia estas.
        listOf("are", "more", "here", "there", "before", "sure").forEach {
            assertEquals(it, AnswerChecker.normalize(it))
        }
    }

    @Test
    fun `una respuesta de verdad equivocada sigue estando mal`() {
        val r = AnswerChecker.check("the city market", "the city center")
        assertTrue(r is AnswerChecker.Result.Wrong)
    }

    @Test
    fun `unificar variantes no rompe las contracciones`() {
        aceptadaSinAviso("I'm not travelling", "I am not traveling")
    }
}
