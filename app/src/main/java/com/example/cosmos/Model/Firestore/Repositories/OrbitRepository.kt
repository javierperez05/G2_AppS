package com.example.cosmos.Model.Firestore.Repositories

import android.util.Log
import com.example.cosmos.Model.Users.Group
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrbitRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("groups")

    // ── Crear grupo ───────────────────────────────────────────────────────────

    fun createGroup(group: Group, onResult: (Boolean) -> Unit) {
        val newId = db.document().id
        val groupWithId = group.copy(id = newId)
        db.document(newId).set(groupWithId)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Añadir usuario a grupo ────────────────────────────────────────────────

    fun addUserToGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("memberIds", FieldValue.arrayUnion(userId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                Log.e("COSMOS", "Error al añadir usuario: ${e.message}")
                onResult(false)
            }
    }

    // ── Grupos del usuario — callback (compatible con código existente) ────────

    fun getUserGroups(userId: String, onResult: (List<Group>) -> Unit) {
        db.whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val groups = snapshot?.toObjects(Group::class.java) ?: emptyList()
                onResult(groups)
            }
    }

    // ── Grupos del usuario — Flow (para ViewModels con StateFlow) ─────────────

    fun getUserGroupsFlow(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = db
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(Group::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Obtener grupo por ID ──────────────────────────────────────────────────

    suspend fun getGroupById(groupId: String): Result<Group> {
        return try {
            val doc = db.document(groupId).get().await()
            val group = doc.toObject(Group::class.java)
                ?: return Result.failure(Exception("Grupo no encontrado"))
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}