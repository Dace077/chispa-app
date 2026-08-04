package com.chispa.ingles.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Datos de la propia app y acceso a la página de descargas.
 *
 * Aquí está el único enlace externo de todo el proyecto. Chispa no declara el
 * permiso de INTERNET, así que no puede abrirlo por su cuenta: se lo pasa al
 * navegador del sistema mediante un Intent. Es el navegador quien se conecta,
 * no la app.
 */
object AppInfo {

    /** Página de releases. Cambia esto si mueves el proyecto de sitio. */
    const val RELEASES_URL = "https://github.com/Dace077/chispa-app/releases/latest"

    fun versionName(context: Context): String =
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull() ?: "desconocida"

    fun versionCode(context: Context): Long =
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)

    /**
     * Abre la página de descargas en el navegador.
     * @return false si el dispositivo no tiene ningún navegador que la atienda.
     */
    fun openReleasesPage(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
