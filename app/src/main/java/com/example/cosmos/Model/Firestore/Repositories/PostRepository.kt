package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Colección: posts/{id}
 *      Cada post tiene: userId, eventId, rating, comment, imageUrls,
 *      memberIds, createdAt. No se usa orderBy en las queries
 *      compuestas para evitar necesitar índices compuestos en
 *      Firestore; el sort se hace en memoria (sortedByDescending).
 *
 *  getPostsByUserIds — chunked por 10
 *      Firestore whereIn tiene máximo 30 elementos, pero para mayor
 *      seguridad se divide en chunks de 10. Cada chunk lanza una
 *      query independiente. Se usa synchronized(collected) para
 *      acumular resultados de forma thread-safe. Cuando pending
 *      llega a 0, se devuelve la lista combinada.
 *
 *  hasPosted — check de unicidad
 *      Antes de publicar, se comprueba si ya existe un post del
 *      mismo userId+eventId. Garantiza que no haya duplicados
 *      (un post por usuario por evento).
 * ═══════════════════════════════════════════════════════════════════
 */

import com.example.cosmos.Model.Actions.Post
import com.google.firebase.firestore.FirebaseFirestore
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
            .get()
            .addOnSuccessListener { posts ->
                onResult(posts.toObjects(Post::class.java).sortedByDescending { it.createdAt })
            }
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
