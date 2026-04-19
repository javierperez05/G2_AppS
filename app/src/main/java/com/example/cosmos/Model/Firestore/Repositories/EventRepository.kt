package com.example.cosmos.Model.Firestore.Repositories

import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Event.EventItem
import com.example.cosmos.Model.Firestore.FirebaseModule
import com.example.cosmos.Model.Users.User

class EventRepository {
    private val db = FirebaseModule.eventsCollection

    // Crear un evento nuevo
    fun createEvent(event: Event, onResult: (Boolean) -> Unit) {
        val newId = db.document().id
        val eventWithId = event.copy(id = newId)

        db.document(newId).set(eventWithId)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // Obtener todos los eventos donde el usuario es miembro
    fun getEventsForUser(userId: String, onResult: (List<Event>) -> Unit) {
        db.whereArrayContains("memberIds", userId) // Buscamos el ID en la lista de miembros
            .addSnapshotListener { snapshot, _ ->
                val events = snapshot?.toObjects(Event::class.java) ?: emptyList()
                onResult(events)
            }
    }

    // Actualizar un ítem del evento (ej. marcar quién lo trae)
    fun updateEventItems(eventId: String, newItems: List<EventItem>, onResult: (Boolean) -> Unit) {
        db.document(eventId).update("items", newItems)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }


    /**
     * // Función para asignar una responsabilidad (EventItem)
     *     fun assignResponsibility(eventId: String, itemId: String, userId: String, onResult: (Boolean) -> Unit) {
     *         // Buscamos el evento, localizamos el ítem y le asignamos el usuario
     *         // Nota: En Firestore, esto es más fácil si usas una subcolección de 'items'
     *         db.document(eventId).collection("items").document(itemId)
     *             .update("responsability", FieldValue.arrayUnion(userId))
     *             .addOnSuccessListener { onResult(true) }
     *     }
     *
     *     // Función para el feed de News (ver eventos pasados con ratings)
     *     fun getNewsFeed(friendIds: List<String>, onResult: (List<Event>) -> Unit) {
     *         // Traemos eventos finalizados de mis amigos
     *         db.whereIn("adminIds", friendIds)
     *             .whereEqualTo("status", "FINISHED") // Necesitarás un campo status
     *             .orderBy("date", Query.Direction.DESCENDING)
     *             .get()
     *             .addOnSuccessListener { snapshot ->
     *                 onResult(snapshot.toObjects(Event::class.java))
     *             }
     *     }
     */
}