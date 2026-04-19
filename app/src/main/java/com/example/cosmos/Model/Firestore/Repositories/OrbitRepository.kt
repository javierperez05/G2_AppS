package com.example.cosmos.Model.Firestore.Repositories

import android.util.Log
import com.example.cosmos.Model.Firestore.FirebaseModule
import com.example.cosmos.Model.Users.Group
import com.google.firebase.firestore.FieldValue

class OrbitRepository {
    private val db = FirebaseModule.groupsCollection

    // Crear un nuevo grupo (Órbita)
    fun createGroup(group: Group, onResult: (Boolean) -> Unit) {
        val newId = db.document().id
        val groupWithId = group.copy(id = newId)

        db.document(newId).set(groupWithId)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }


    //Añadir un usuario a un grupo
    fun addUserToGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("members", FieldValue.arrayUnion(userId)) // "members" debe ser el nombre del campo en Firestore
            .addOnSuccessListener {
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e("COSMOS", "Error al añadir usuario: ${e.message}")
                onResult(false)
            }
    }

    // Traer los grupos de un usuario
    fun getUserGroups(userId: String, onResult: (List<Group>) -> Unit) {
        db.whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val groups = snapshot?.toObjects(Group::class.java) ?: emptyList()
                onResult(groups)
            }
    }
}