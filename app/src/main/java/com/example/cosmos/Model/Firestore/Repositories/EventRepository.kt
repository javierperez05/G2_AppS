package com.example.cosmos.Model.Firestore.Repositories

import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventItem
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val db get() = firestore.collection("events")

    fun createEvent(event: Event, onResult: (Boolean) -> Unit) {
        val docRef     = db.document()
        val finalEvent = event.copy(id = docRef.id)
        docRef.set(finalEvent)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun getUserEvents(userId: String, onResult: (List<Event>) -> Unit) {
        db.whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val events = snapshot?.toObjects(Event::class.java) ?: emptyList()
                onResult(events.sortedBy { it.date })
            }
    }

    fun getEventsByIds(eventIds: List<String>, onResult: (List<Event>) -> Unit) {
        if (eventIds.isEmpty()) { onResult(emptyList()); return }
        db.whereIn(FieldPath.documentId(), eventIds.take(30))
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toObjects(Event::class.java).sortedBy { it.date })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun updateEventItems(eventId: String, newItems: List<EventItem>, onResult: (Boolean) -> Unit) {
        db.document(eventId).update("items", newItems)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}