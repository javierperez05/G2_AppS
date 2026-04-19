package com.example.cosmos.Model.Firestore.Repositories

import com.example.cosmos.Model.Firestore.FirebaseModule
import com.example.cosmos.Model.Users.User

class UserRepository {
    private val db = FirebaseModule.usersCollection

    // Registro manual
    fun registerUser(user: User, onResult: (Boolean, String?) -> Unit) {
        // Buscamos si el email ya existe antes de crear
        db.whereEqualTo("email", user.email).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    val newId = db.document().id
                    val userWithId = user.copy(id = newId)

                    db.document(newId).set(userWithId)
                        .addOnSuccessListener { onResult(true, "Usuario creado") }
                        .addOnFailureListener { onResult(false, it.message) }
                } else {
                    onResult(false, "El email ya está registrado")
                }
            }
    }

    // Login manual
    fun loginUser(email: String, pass: String, onResult: (User?) -> Unit) {
        db.whereEqualTo("email", email)
            .whereEqualTo("password", pass) // Muy básico, idealmente sería con hash
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val user = documents.documents[0].toObject(User::class.java)
                    onResult(user)
                } else {
                    onResult(null)
                }
            }
    }
}