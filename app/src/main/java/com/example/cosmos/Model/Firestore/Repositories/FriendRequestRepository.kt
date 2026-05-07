package com.example.cosmos.Model.Firestore.Repositories

import com.example.cosmos.Model.Actions.FriendRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRequestRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("friendRequests")
    private val usersDb get() = firestore.collection("users")

    fun sendRequest(request: FriendRequest, onResult: (Boolean) -> Unit) {
        // Check if a pending request already exists in either direction
        db.whereEqualTo("fromId", request.fromId)
            .whereEqualTo("toId", request.toId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
                    onResult(false) // Already sent
                    return@addOnSuccessListener
                }
                val docRef = db.document()
                val finalRequest = request.copy(id = docRef.id)
                docRef.set(finalRequest)
                    .addOnSuccessListener { onResult(true) }
                    .addOnFailureListener { onResult(false) }
            }
            .addOnFailureListener { onResult(false) }
    }

    fun getIncomingRequests(userId: String, onResult: (List<FriendRequest>) -> Unit) {
        db.whereEqualTo("toId", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { onResult(it.toObjects(FriendRequest::class.java)) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun getSentPendingIds(userId: String, onResult: (Set<String>) -> Unit) {
        db.whereEqualTo("fromId", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snapshot ->
                val ids = snapshot.toObjects(FriendRequest::class.java).map { it.toId }.toSet()
                onResult(ids)
            }
            .addOnFailureListener { onResult(emptySet()) }
    }

    fun acceptRequest(requestId: String, fromId: String, toId: String, onResult: (Boolean) -> Unit) {
        val batch = firestore.batch()
        // Update request status
        batch.update(db.document(requestId), "status", "accepted")
        // Add each user to the other's friends list
        batch.update(usersDb.document(fromId), "friends", FieldValue.arrayUnion(toId))
        batch.update(usersDb.document(toId), "friends", FieldValue.arrayUnion(fromId))
        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun rejectRequest(requestId: String, onResult: (Boolean) -> Unit) {
        db.document(requestId).update("status", "rejected")
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
