package com.example.cosmos.Model.Firestore.Repositories

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
                onResult(snapshot.documents.firstOrNull()?.toObject(User::class.java))
            }
            .addOnFailureListener { onResult(null) }
    }

    fun registerUser(user: User, onResult: (Boolean, String) -> Unit) {
        db.whereEqualTo("email", user.email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    onResult(false, "Este email ya está registrado")
                    return@addOnSuccessListener
                }
                val newId   = db.document().id
                val newUser = user.copy(id = newId)
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
}