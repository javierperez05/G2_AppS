package com.example.cosmos.Model.Firestore.Repositories

import com.example.cosmos.Model.Actions.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("posts")

    fun createPost(post: Post, onResult: (Boolean) -> Unit) {
        val docRef = db.document()
        val finalPost = post.copy(id = docRef.id)
        docRef.set(finalPost)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // Get posts by a list of userIds (for feed - your posts + orbit members' posts)
    fun getPostsByUserIds(userIds: List<String>, onResult: (List<Post>) -> Unit) {
        if (userIds.isEmpty()) { onResult(emptyList()); return }
        val chunks = userIds.chunked(10)
        val collected = mutableListOf<Post>()
        var pending = chunks.size

        chunks.forEach { chunk ->
            db.whereIn("userId", chunk)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    synchronized(collected) {
                        collected.addAll(snapshot.toObjects(Post::class.java))
                        pending--
                        if (pending == 0) onResult(collected.sortedByDescending { it.createdAt })
                    }
                }
                .addOnFailureListener {
                    synchronized(collected) {
                        pending--
                        if (pending == 0) onResult(collected.sortedByDescending { it.createdAt })
                    }
                }
        }
    }

    // Get posts by a specific user (for profile)
    fun getPostsByUser(userId: String, onResult: (List<Post>) -> Unit) {
        db.whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { onResult(it.toObjects(Post::class.java)) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // Get a specific post by userId + eventId
    fun getPostByUserAndEvent(userId: String, eventId: String, onResult: (Post?) -> Unit) {
        db.whereEqualTo("userId", userId)
            .whereEqualTo("eventId", eventId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toObjects(Post::class.java).firstOrNull())
            }
            .addOnFailureListener { onResult(null) }
    }

    // Check if user already posted for an event
    fun hasPosted(userId: String, eventId: String, onResult: (Boolean) -> Unit) {
        db.whereEqualTo("userId", userId)
            .whereEqualTo("eventId", eventId)
            .limit(1)
            .get()
            .addOnSuccessListener { onResult(!it.isEmpty) }
            .addOnFailureListener { onResult(false) }
    }
}
