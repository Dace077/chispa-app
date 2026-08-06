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

    /**
     * Ficha en Google Play, que es el único canal de distribución.
     *
     * Antes se repartía un APK por enlace directo. Ya no: se retiró para que
     * exista una sola vía de actualización y ningún usuario se quede en una
     * versión vieja sin enterarse.
     */
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.chispa.ingles"

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

    /** Identificador de la Play Store en el sistema. */
    private const val PLAY_STORE = "com.android.vending"

    /**
     * Si esta copia se instaló desde Google Play.
     *
     * Es la diferencia entre las dos formas de actualizarse, y no es un detalle
     * cosmético: la política de Play prohíbe que una app distribuida por la
     * tienda se actualice por cualquier vía que no sea la propia tienda. Mandar
     * a un usuario de Play a bajarse un APK de GitHub es motivo de rechazo.
     */
    fun installedFromPlay(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val origen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        origen == PLAY_STORE
    }.getOrDefault(false)

    /**
     * Abre la ficha de la app en Google Play.
     *
     * La app no descarga ni instala nada por su cuenta: solo lanza un Intent y
     * es Play quien se encarga. Chispa no declara el permiso de INTERNET y no
     * podría hacerlo aunque quisiera.
     *
     * @return false si el dispositivo no tiene nada que atienda el enlace.
     */
    fun openUpdatePage(context: Context): Boolean {
        // market:// abre la app de Play directamente, sin pasar por el navegador.
        val enPlay = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(enPlay); true }.getOrDefault(false)) return true

        // Si la app de Play no está instalada, su web sirve igual.
        val enWeb = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(enWeb); true }.getOrDefault(false)
    }
}
