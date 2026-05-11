package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Repository
 *      Capa que aísla al resto de la app de los detalles de cómo se
 *      accede a los datos. Los ViewModels no saben si los datos vienen
 *      de Firestore, de una API, de una caché local… solo llaman al
 *      repositorio y este resuelve cómo obtenerlos.
 *
 *  Callbacks (onResult: (T) -> Unit)
 *      Las llamadas a Firestore son asíncronas: no bloquean el hilo
 *      principal y devuelven el resultado más tarde cuando llega de la red.
 *      En vez de usar coroutines (suspend fun), usamos callbacks para
 *      mantenernos cerca del estilo nativo del SDK de Firebase.
 *      El caller (ViewModel) pasa una función lambda que se ejecutará
 *      cuando el resultado esté listo.
 *
 *  @Singleton
 *      Solo existe una instancia de UserRepository en toda la app.
 *      Tiene sentido porque no guarda estado propio (solo tiene la
 *      referencia a Firestore) y así Hilt no crea objetos innecesarios.
 *
 *  whereGreaterThanOrEqualTo + \uf8ff (búsqueda por prefijo)
 *      Firestore no tiene búsqueda full-text nativa. El truco estándar
 *      para buscar "que empiece por X" es usar dos condiciones:
 *        >= "x"         (mayor o igual que el texto buscado)
 *        <= "x\uf8ff"   (\uf8ff es el carácter Unicode más alto posible,
 *                        así el rango captura todo lo que empiece por "x")
 *      Requiere índice compuesto en Firestore sobre "usernameLower".
 *      Se usa usernameLower (todo minúsculas) para que la búsqueda
 *      no sea case-sensitive.
 *
 *  Auto-migración de usernameLower
 *      Los usuarios creados antes de que añadiéramos el campo
 *      usernameLower no lo tienen en Firestore. Al hacer login,
 *      si detectamos que falta, lo añadimos automáticamente.
 *      Así no necesitamos un script de migración masivo.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.util.Log
import com.example.cosmos.Model.Users.User
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("users")

    fun loginUser(email: String, password: String, onResult: (User?) -> Unit) {
        db.whereEqualTo("email", email)
            .whereEqualTo("password", password)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.i("UserRepository", "Login query returned ${snapshot.size()} results for email: $email")
                val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
                // Auto-migración: si el usuario existe pero no tiene usernameLower
                // (fue creado antes de añadir el campo), lo calculamos y guardamos.
                // Esto sucede solo una vez por usuario, en su siguiente login.
                if (user != null && user.usernameLower.isNullOrEmpty() && !user.username.isNullOrEmpty()) {
                    db.document(user.id ?: "").update("usernameLower", user.username.lowercase())
                }
                onResult(user)
            }
            .addOnFailureListener { onResult(null) }
    }

    fun registerUser(user: User, onResult: (Boolean, String) -> Unit) {
        // Primero comprobamos si el email ya existe para dar un error claro
        db.whereEqualTo("email", user.email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    onResult(false, "Este email ya está registrado")
                    return@addOnSuccessListener
                }
                // Generamos el ID antes de crear el documento para poder
                // incluirlo dentro del propio documento (self-referential ID)
                val newId   = db.document().id
                val newUser = user.copy(id = newId, usernameLower = user.username?.lowercase())
                db.document(newId).set(newUser)
                    .addOnSuccessListener { onResult(true, "Cuenta creada correctamente") }
                    .addOnFailureListener { onResult(false, "Error al crear la cuenta") }
            }
            .addOnFailureListener { onResult(false, "Error de conexión") }
    }

    fun getUserById(userId: String, onResult: (User?) -> Unit) {
        db.document(userId).get()
            .addOnSuccessListener { onResult(it.toObject(User::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    fun getFriends(userId: String, onResult: (List<User>) -> Unit) {
        db.document(userId).get()
            .addOnSuccessListener { doc ->
                val friendIds = doc.get("friends") as? List<String> ?: emptyList()
                if (friendIds.isEmpty()) { onResult(emptyList()); return@addOnSuccessListener }
                // whereIn tiene límite de 30 elementos. Para listas de amigos más grandes
                // habría que hacer chunked(30), pero de momento con take(30) es suficiente.
                db.whereIn("id", friendIds.take(30)).get()
                    .addOnSuccessListener { onResult(it.toObjects(User::class.java)) }
                    .addOnFailureListener { onResult(emptyList()) }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun getUsersByIds(userIds: List<String>, onResult: (List<User>) -> Unit) {
        if (userIds.isEmpty()) { onResult(emptyList()); return }
        db.whereIn("id", userIds.take(30)).get()
            .addOnSuccessListener { onResult(it.toObjects(User::class.java)) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // Método genérico para actualizar cualquier campo del usuario sin
    // sobreescribir el documento entero. update() solo toca los campos
    // del mapa y deja el resto intacto.
    fun updateUserFields(userId: String, fields: Map<String, Any?>, onResult: (Boolean) -> Unit) {
        if (userId.isEmpty()) { onResult(false); return }
        db.document(userId).update(fields)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // Búsqueda por prefijo de username (case-insensitive gracias a usernameLower).
    // El rango [lower, lower+\uf8ff] captura todos los strings que empiecen por "lower".
    // limit(20) para no sobrecargar la query con resultados innecesarios.
    fun searchByUsername(query: String, excludeUserId: String, onResult: (List<User>) -> Unit) {
        if (query.isBlank()) { onResult(emptyList()); return }
        val lower = query.lowercase()
        db.whereGreaterThanOrEqualTo("usernameLower", lower)
            .whereLessThanOrEqualTo("usernameLower", lower + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                // Excluimos al propio usuario de los resultados de búsqueda
                onResult(snapshot.toObjects(User::class.java).filter { it.id != excludeUserId })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
