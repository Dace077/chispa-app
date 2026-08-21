package com.chispa.ingles.certificates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Saca un archivo de la app hacia donde el usuario quiera.
 *
 * Chispa no declara INTERNET, así que no manda nada a ninguna parte: construye
 * un Intent y deja que el usuario elija la app que lo reciba. Quien envía es esa
 * otra app, con su propio permiso, y solo si la persona lo pide expresamente.
 */
object CertificateSharing {

    private fun uriDe(context: Context, archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)

    /** Abre el selector de "Compartir" con el PDF adjunto. */
    fun compartir(context: Context, archivo: File, titulo: String) {
        val uri = uriDe(context, archivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, titulo)
            putExtra(Intent.EXTRA_TITLE, titulo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(envio, "Compartir certificado")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Abre el PDF con el visor que tenga el usuario.
     *
     * Puede no haber ninguno: hay teléfonos sin lector de PDF instalado. Se
     * devuelve `false` en vez de reventar, para que la pantalla ofrezca
     * compartir, que siempre funciona.
     */
    fun abrir(context: Context, archivo: File): Boolean {
        val uri = uriDe(context, archivo)
        val ver = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(ver) }.isSuccess
    }
}
