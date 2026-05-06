package com.example.cosmos.Model.Firestore.Repositories

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
