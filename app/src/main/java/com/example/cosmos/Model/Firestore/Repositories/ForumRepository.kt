package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Subcolección: events/{eventId}/threads/{threadId}
 *      Cada thread tiene texto, autor y un array de replies embebidas.
 *      Las replies son objetos dentro del campo "replies" del thread
 *      (no documentos separados).
 *
 *  listenThreads — listener en tiempo real
 *      Devuelve un ListenerRegistration que escucha cambios en los
 *      threads. Cada vez que alguien publica o responde, el callback
 *      se invoca automáticamente con la lista actualizada.
 *      EventDetailViewModel guarda la referencia y llama .remove()
 *      en onCleared() para limpiar el listener.
 *
 *  postReply — runTransaction
 *      Las replies se añaden con una transacción Firestore para
 *      evitar race conditions: si dos personas responden al mismo
 *      tiempo, la transacción lee el estado actual, añade la reply
 *      y escribe. Si hubo un cambio concurrente, Firestore reintenta.
 * ═══════════════════════════════════════════════════════════════════
 */

import com.example.cosmos.Model.Event.ForumReply
import com.example.cosmos.Model.Event.ForumThread
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForumRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun threadsRef(eventId: String) =
        firestore.collection("events").document(eventId).collection("threads")

    fun listenThreads(eventId: String, onResult: (List<ForumThread>) -> Unit): ListenerRegistration {
        return threadsRef(eventId)
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, _ ->
                val threads = snapshot?.toObjects(ForumThread::class.java) ?: emptyList()
                onResult(threads)
            }
    }

    fun postThread(eventId: String, thread: ForumThread, onResult: (Boolean) -> Unit) {
        val ref = threadsRef(eventId).document()
        ref.set(thread.copy(id = ref.id))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun postReply(
        eventId: String,
        threadId: String,
        reply: ForumReply,
        onResult: (Boolean) -> Unit
    ) {
        val ref = threadsRef(eventId).document(threadId)
        firestore.runTransaction { tx ->
            val current = tx.get(ref).toObject(ForumThread::class.java) ?: return@runTransaction
            val updated = current.replies + reply.copy(id = UUID.randomUUID().toString())
            tx.update(ref, "replies", updated)
        }
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
