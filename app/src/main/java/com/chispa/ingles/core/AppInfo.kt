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
     * Lleva al usuario a donde le toque actualizarse según de dónde vino:
     * a su ficha de Play si la instaló de ahí, o a la página de descargas si
     * se la pasaron por enlace.
     *
     * En ningún caso descarga ni instala nada la propia app: solo lanza un
     * Intent. Chispa no declara el permiso de INTERNET y no podría hacerlo.
     *
     * @return false si el dispositivo no tiene nada que atienda el enlace.
     */
    fun openUpdatePage(context: Context): Boolean {
        if (installedFromPlay(context)) {
            // market:// abre la app de Play directamente, sin pasar por el navegador.
            val enPlay = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(enPlay); true }.getOrDefault(false)) return true

            // Si la app de Play no está (algunos dispositivos), su web sirve igual.
            val enWeb = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(enWeb); true }.getOrDefault(false)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
