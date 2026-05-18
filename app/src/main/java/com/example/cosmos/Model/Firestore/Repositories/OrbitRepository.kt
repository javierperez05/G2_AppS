package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Colección: groups/{id} ("órbitas" en la UI)
 *      Cada grupo tiene: memberIds, adminIds, invitedIds, eventIds.
 *      memberIds son miembros activos, invitedIds son pendientes.
 *
 *  Dos formas de obtener grupos: callback vs Flow
 *      - getUserGroups(callback): usa addSnapshotListener con callback
 *        directo. Compatible con el código existente que no usa coroutines.
 *      - getUserGroupsFlow(): devuelve Flow<List<Group>> usando
 *        callbackFlow. Mejor para ViewModels que colectan con StateFlow.
 *      Ambas son listeners en tiempo real (se actualizan automáticamente).
 *
 *  acceptGroupInvite — batch
 *      Quita al usuario de invitedIds y lo añade a memberIds en una
 *      sola operación atómica (batch). Evita el estado intermedio
 *      donde no está ni invitado ni como miembro.
 *
 *  addEventToGroup
 *      Cuando se crea un evento desde GroupDetail, se vincula al grupo
 *      añadiendo el eventId al array eventIds con arrayUnion.
 * ═══════════════════════════════════════════════════════════════════
 */

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

    // ── Añadir evento al grupo ────────────────────────────────────────────────

    fun addEventToGroup(groupId: String, eventId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("eventIds", FieldValue.arrayUnion(eventId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Invitar usuario ───────────────────────────────────────────────────────

    fun inviteUserToGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("invitedIds", FieldValue.arrayUnion(userId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Aceptar invitación ────────────────────────────────────────────────────

    fun acceptGroupInvite(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        val batch = firestore.batch()
        val ref = db.document(groupId)
        batch.update(ref, "invitedIds", FieldValue.arrayRemove(userId))
        batch.update(ref, "memberIds", FieldValue.arrayUnion(userId))
        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Rechazar / cancelar invitación ────────────────────────────────────────

    fun rejectGroupInvite(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("invitedIds", FieldValue.arrayRemove(userId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Salir del grupo ───────────────────────────────────────────────────────

    fun leaveGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("memberIds", FieldValue.arrayRemove(userId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Expulsar miembro (solo admins) ────────────────────────────────────────

    fun removeMemberFromGroup(groupId: String, userId: String, onResult: (Boolean) -> Unit) {
        db.document(groupId)
            .update("memberIds", FieldValue.arrayRemove(userId))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Grupos donde el usuario está invitado (flow) ──────────────────────────

    fun getInvitedGroupsFlow(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = db
            .whereArrayContains("invitedIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(Group::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }
}