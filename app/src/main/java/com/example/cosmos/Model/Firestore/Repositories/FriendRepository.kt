package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Operaciones sobre la lista friends[] del User
 *      Cada User tiene un campo friends: List<String> con los IDs
 *      de sus amigos. Este repositorio lee esa lista y carga los
 *      documentos User correspondientes.
 *
 *  searchFriends — filtro local
 *      Firestore no permite combinar whereIn (para IDs de amigos)
 *      con inequality (para buscar por username). La solución:
 *      cargar todos los amigos con whereIn y filtrar en memoria
 *      por username. Funciona bien para listas <30 amigos.
 *
 *  searchAllUsers — búsqueda por prefijo
 *      Usa usernameLower con whereGreaterThanOrEqualTo + "\uf8ff"
 *      para buscar por prefijo. El carácter \uf8ff es el último
 *      en el orden UTF-8 de Firestore, así que "abc\uf8ff" incluye
 *      todo lo que empiece por "abc".
 *
 *  removeFriend — solo quita de un lado
 *      Actualmente solo quita el friendId del array del usuario
 *      actual (no del otro usuario). Es un TODO pendiente: debería
 *      ser un batch que quite de ambos lados.
 * ═══════════════════════════════════════════════════════════════════
 */

import com.example.cosmos.Model.Users.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("users")

    // ── Buscar dentro de tus amigos ───────────────────────────────────────────

    fun searchFriends(
        currentUserId: String,
        query: String,
        onResult: (List<User>) -> Unit
    ) {
        if (query.isBlank()) { onResult(emptyList()); return }

        // Firestore no permite whereIn + inequality en campos distintos,
        // así que cargamos amigos por IDs y filtramos localmente por username
        db.document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val friendIds = doc.get("friends") as? List<String> ?: emptyList()
                if (friendIds.isEmpty()) { onResult(emptyList()); return@addOnSuccessListener }

                db.whereIn("id", friendIds.take(30)).get()
                    .addOnSuccessListener { snapshot ->
                        val lowerQuery = query.lowercase()
                        val filtered = snapshot.toObjects(User::class.java).filter {
                            it.username?.lowercase()?.contains(lowerQuery) == true
                        }
                        onResult(filtered)
                    }
                    .addOnFailureListener { onResult(emptyList()) }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ── Buscar en toda la app (modo exploración) ──────────────────────────────

    fun searchAllUsers(
        query: String,
        currentUserId: String,
        onResult: (List<User>) -> Unit
    ) {
        if (query.isBlank()) { onResult(emptyList()); return }

        val lower = query.lowercase()
        db.whereGreaterThanOrEqualTo("usernameLower", lower)
            .whereLessThanOrEqualTo("usernameLower", lower + "\uf8ff")
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(User::class.java)
                    .filter { it.id != currentUserId }
                onResult(users)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ── Cargar lista de amigos completa ───────────────────────────────────────

    fun getFriends(currentUserId: String, onResult: (List<User>) -> Unit) {
        db.document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val friendIds = doc.get("friends") as? List<String> ?: emptyList()
                if (friendIds.isEmpty()) { onResult(emptyList()); return@addOnSuccessListener }
                db.whereIn("id", friendIds.take(30)).get()
                    .addOnSuccessListener { onResult(it.toObjects(User::class.java)) }
                    .addOnFailureListener { onResult(emptyList()) }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ── Añadir amigo ──────────────────────────────────────────────────────────
    // Opción A: añade directo sin solicitud
    // TODO: migrar a solicitudes — añadir friendRequests/{id} con status: "pending"

    fun addFriend(currentUserId: String, friendId: String, onResult: (Boolean) -> Unit) {
        db.document(currentUserId)
            .update("friends", FieldValue.arrayUnion(friendId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Eliminar amigo ────────────────────────────────────────────────────────

    fun removeFriend(currentUserId: String, friendId: String, onResult: (Boolean) -> Unit) {
        db.document(currentUserId)
            .update("friends", FieldValue.arrayRemove(friendId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Comprobar si ya es amigo ──────────────────────────────────────────────

    fun isFriend(currentUserId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val friendIds = doc.get("friends") as? List<String> ?: emptyList()
                onResult(friendIds.contains(userId))
            }
            .addOnFailureListener { onResult(false) }
    }
}
