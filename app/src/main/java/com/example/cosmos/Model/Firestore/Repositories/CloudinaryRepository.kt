package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Cloudinary
 *      Servicio de hosting de imágenes con tier gratuito (25GB).
 *      Las imágenes se suben directamente desde el móvil sin pasar
 *      por un servidor propio. Cloudinary devuelve una URL pública
 *      que guardamos en Firestore y cargamos con Glide.
 *      Bonus: puedes transformar imágenes cambiando la URL:
 *        original:   .../upload/foto.jpg
 *        thumbnail:  .../upload/c_fill,w_300,h_300/foto.jpg
 *      Sin código extra, solo modificando el string de la URL.
 *
 *  TODO: credenciales
 *      CLOUD_NAME y UPLOAD_PRESET están marcados como TODO.
 *      Cuando tengas cuenta en cloudinary.com, reemplaza los valores
 *      en el companion object de esta clase. Solo esas dos líneas.
 *
 *  Multipart/form-data
 *      Formato estándar HTTP para subir archivos a un servidor.
 *      El "boundary" es un separador aleatorio que marca dónde
 *      empieza y termina cada campo del formulario.
 *      Aquí enviamos dos campos: upload_preset (texto) y file (bytes).
 *
 *  Thread + Handler(Looper.getMainLooper())
 *      Las operaciones de red NO pueden hacerse en el hilo principal
 *      (Main Thread) porque bloquearían la UI y Android lanzaría
 *      un NetworkOnMainThreadException.
 *      Solución: lanzamos un Thread de fondo para la subida.
 *      Cuando termina, usamos Handler(Looper.getMainLooper()).post()
 *      para volver al hilo principal antes de llamar al callback,
 *      porque actualizar vistas solo se puede hacer desde Main Thread.
 *
 *  @ApplicationContext
 *      El repositorio necesita Context para leer URIs de imágenes
 *      del almacenamiento del móvil. No puede usar el contexto de
 *      un Activity (memory leak) — Hilt nos inyecta el contexto
 *      de la Application que vive toda la sesión sin fugas.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudinaryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // ─────────────────────────────────────────────────────────────────────
        // TODO: Pon aquí tus credenciales cuando tengas cuenta en Cloudinary.
        //
        //  1. CLOUD_NAME  →  cloudinary.com/console  (esquina superior izquierda)
        //  2. UPLOAD_PRESET →  Settings → Upload → Upload presets
        //                      → Add upload preset → Signing mode: Unsigned
        //                      Guarda el nombre que le pongas.
        // ─────────────────────────────────────────────────────────────────────
        private const val CLOUD_NAME    = "TU_CLOUD_NAME_AQUI"
        private const val UPLOAD_PRESET = "TU_UPLOAD_PRESET_AQUI"

        private const val UPLOAD_URL    = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

        // Lado largo máximo antes de subir. Cloudinary almacena esta versión
        // reducida, ahorrando ancho de banda sin perder calidad visual notable.
        private const val MAX_DIM      = 1200
        private const val JPEG_QUALITY = 85
    }

    /**
     * Sube una imagen a Cloudinary y devuelve su secure_url, o null si falla.
     * La URL se puede usar directamente con Glide.
     *
     * Para mostrar miniaturas sin descargar la imagen completa, modifica la URL:
     *   url.replace("/upload/", "/upload/c_fill,w_400,h_400/")
     */
    fun uploadImage(uri: Uri, onResult: (String?) -> Unit) {
        Thread {
            try {
                val imageBytes = readAndCompress(uri) ?: run {
                    post(onResult, null)
                    return@Thread
                }

                val boundary = "cosmos${System.currentTimeMillis()}"
                val conn = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput      = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                // Construimos el cuerpo multipart escribiendo bytes directamente
                // (sin BufferedWriter) para evitar problemas con los bytes de la imagen
                conn.outputStream.use { out ->
                    val CRLF = "\r\n"
                    // Campo de texto: upload_preset
                    out.write("--$boundary$CRLF".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"upload_preset\"$CRLF$CRLF".toByteArray())
                    out.write("$UPLOAD_PRESET$CRLF".toByteArray())
                    // Campo binario: la imagen
                    out.write("--$boundary$CRLF".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"$CRLF".toByteArray())
                    out.write("Content-Type: image/jpeg$CRLF$CRLF".toByteArray())
                    out.write(imageBytes)
                    out.write("$CRLF--$boundary--$CRLF".toByteArray())
                }

                // Parseamos solo el campo secure_url de la respuesta JSON
                val secureUrl = if (conn.responseCode == 200) {
                    JSONObject(conn.inputStream.bufferedReader().readText())
                        .getString("secure_url")
                } else null

                conn.disconnect()
                post(onResult, secureUrl)

            } catch (e: Exception) {
                post(onResult, null)
            }
        }.start()
    }

    /**
     * Sube varias imágenes en secuencia (una tras otra, no en paralelo)
     * y devuelve la lista de URLs. Las que fallen no se incluyen.
     * Recursivo: uploadNext() se llama a sí misma desde el callback
     * de uploadImage() para encadenar las subidas sin bloquear nada.
     */
    fun uploadImages(uris: List<Uri>, onResult: (List<String>) -> Unit) {
        if (uris.isEmpty()) { onResult(emptyList()); return }
        val urls  = mutableListOf<String>()
        var index = 0

        fun next() {
            if (index >= uris.size) { onResult(urls); return }
            uploadImage(uris[index++]) { url ->
                if (url != null) urls.add(url)
                next()
            }
        }
        next()
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    // Lee la imagen desde la URI, la escala si supera MAX_DIM en cualquier lado,
    // y la comprime a JPEG. El ratio original se mantiene (no se deforma).
    private fun readAndCompress(uri: Uri): ByteArray? = try {
        val input    = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input)
        input.close()

        val scaled = if (original.width > MAX_DIM || original.height > MAX_DIM) {
            val ratio = original.width.toFloat() / original.height
            val (w, h) = if (original.width >= original.height)
                Pair(MAX_DIM, (MAX_DIM / ratio).toInt())
            else
                Pair((MAX_DIM * ratio).toInt(), MAX_DIM)
            Bitmap.createScaledBitmap(original, w, h, true)
        } else {
            original
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        baos.toByteArray()
    } catch (e: Exception) { null }

    // Atajo para postear el resultado al hilo principal antes de llamar al callback
    private fun <T> post(callback: (T) -> Unit, value: T) {
        Handler(Looper.getMainLooper()).post { callback(value) }
    }
}
