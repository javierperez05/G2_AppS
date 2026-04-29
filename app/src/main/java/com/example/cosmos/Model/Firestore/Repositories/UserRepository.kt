package com.example.cosmos.Model.Firestore.Repositories

import com.example.cosmos.Model.Users.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("users")

    // ── Obtener usuario por ID ────────────────────────────────────────────────

    fun getUserById(userId: String, onResult: (User?) -> Unit) {
        db.document(userId).get()
            .addOnSuccessListener { doc ->
                onResult(doc.toObject(User::class.java))
            }
            .addOnFailureListener { onResult(null) }
    }

    // ── Obtener amigos ────────────────────────────────────────────────────────
    // Paso 1: saca la lista de IDs del campo friends del usuario.
    // Paso 2: whereIn para convertir IDs en objetos User.
    // Si la lista está vacía devuelve emptyList() directamente
    // (whereIn falla con lista vacía en Firestore).

    fun getFriends(userId: String, onResult: (List<User>) -> Unit) {
        db.document(userId).get()
            .addOnSuccessListener { doc ->
                val friendIds = doc.get("friends") as? List<String> ?: emptyList()

                if (friendIds.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }

                // Firestore whereIn soporta máximo 30 elementos
                val safeIds = friendIds.take(30)
                db.whereIn("id", safeIds).get()
                    .addOnSuccessListener { snapshot ->
                        onResult(snapshot.toObjects(User::class.java))
                    }
                    .addOnFailureListener { onResult(emptyList()) }
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ── Obtener usuarios por lista de IDs (para detalle de grupo) ─────────────

    fun getUsersByIds(userIds: List<String>, onResult: (List<User>) -> Unit) {
        if (userIds.isEmpty()) { onResult(emptyList()); return }
        val safeIds = userIds.take(30)
        db.whereIn("id", safeIds).get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toObjects(User::class.java))
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ── Crear usuario ─────────────────────────────────────────────────────────

    fun createUser(user: User, onResult: (Boolean) -> Unit) {
        val id = user.id ?: db.document().id
        db.document(id).set(user.copy(id = id))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}