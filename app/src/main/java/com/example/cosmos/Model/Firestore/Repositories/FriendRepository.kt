package com.example.cosmos.Model.Firestore.Repositories

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
