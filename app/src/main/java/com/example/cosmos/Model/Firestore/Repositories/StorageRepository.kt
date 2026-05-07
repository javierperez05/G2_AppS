package com.example.cosmos.Model.Firestore.Repositories

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val storage: FirebaseStorage
) {
    // ── Subir avatar de usuario ─────────────────────────────────────────────
    fun uploadAvatar(userId: String, imageUri: Uri, onResult: (String?) -> Unit) {
        val ref = storage.reference.child("avatars/$userId.jpg")
        ref.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { url -> onResult(url.toString()) }
            .addOnFailureListener { onResult(null) }
    }

    // ── Subir imagen de evento ──────────────────────────────────────────────
    fun uploadEventImage(eventId: String, imageUri: Uri, onResult: (String?) -> Unit) {
        val ref = storage.reference.child("events/$eventId.jpg")
        ref.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { url -> onResult(url.toString()) }
            .addOnFailureListener { onResult(null) }
    }

    // ── Subir imagen de post (varias por post) ─────────────────────────────
    fun uploadPostImage(postId: String, index: Int, imageUri: Uri, onResult: (String?) -> Unit) {
        val ref = storage.reference.child("posts/$postId/$index.jpg")
        ref.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { url -> onResult(url.toString()) }
            .addOnFailureListener { onResult(null) }
    }

    // ── Subir imagen de grupo ───────────────────────────────────────────────
    fun uploadGroupImage(groupId: String, imageUri: Uri, onResult: (String?) -> Unit) {
        val ref = storage.reference.child("groups/$groupId.jpg")
        ref.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { url -> onResult(url.toString()) }
            .addOnFailureListener { onResult(null) }
    }
}
