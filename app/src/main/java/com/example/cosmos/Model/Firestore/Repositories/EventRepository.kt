package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  whereArrayContains vs whereIn
 *      whereArrayContains("memberIds", userId)
 *          "Dame los documentos donde el array memberIds contiene este valor"
 *          Útil para "eventos en los que participo".
 *
 *      whereIn(FieldPath.documentId(), listOfIds)
 *          "Dame los documentos cuyo ID sea uno de estos"
 *          Útil cuando ya tienes los IDs y solo quieres los documentos.
 *          Límite: máximo 30 IDs por query.
 *
 *      whereArrayContainsAny("memberIds", listOfUserIds)
 *          "Dame los documentos donde memberIds contenga AL MENOS UNO de estos"
 *          Útil para "eventos donde participan mis amigos".
 *          Límite: máximo 10 valores en la lista.
 *
 *  chunked(10) para superar el límite de whereArrayContainsAny
 *      Si tienes 25 amigos no puedes hacer whereArrayContainsAny con los 25.
 *      chunked(10) divide la lista en sublistas de 10:
 *        [id1..id10], [id11..id20], [id21..id25]
 *      Se lanzan 3 queries en paralelo y se combinan los resultados.
 *      distinctBy { it.id } evita duplicados si un evento tiene
 *      varios de tus amigos como miembros.
 *
 *  addSnapshotListener (tiempo real) vs .get() (una sola vez)
 *      getUserEvents usa addSnapshotListener: la lista de eventos
 *      se actualiza automáticamente en la UI cuando alguien crea
 *      o modifica un evento sin que el usuario tenga que recargar.
 *      getEventById usa .get(): solo necesitamos el estado actual
 *      del evento al abrir el detalle, no updates en tiempo real.
 *
 *  finishEvent
 *      Solo actualiza el campo "finished" a true.
 *      update() toca solo ese campo, sin sobreescribir el resto del documento.
 * ═══════════════════════════════════════════════════════════════════
 */

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
        // Generamos el ID antes de guardar para incluirlo dentro del documento
        val docRef     = db.document()
        val finalEvent = event.copy(id = docRef.id)
        docRef.set(finalEvent)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // addSnapshotListener: tiempo real. El callback se llama al instante con
    // los datos actuales y cada vez que Firestore actualiza la colección.
    fun getUserEvents(userId: String, onResult: (List<Event>) -> Unit) {
        db.whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, _ ->
                val events = snapshot?.toObjects(Event::class.java) ?: emptyList()
                onResult(events.sortedBy { it.date })
            }
    }

    // Busca eventos por lista de IDs (para GroupDetailFragment).
    // FieldPath.documentId() referencia al ID del documento, no a un campo.
    fun getEventsByIds(eventIds: List<String>, onResult: (List<Event>) -> Unit) {
        if (eventIds.isEmpty()) { onResult(emptyList()); return }
        db.whereIn(FieldPath.documentId(), eventIds.take(30))
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.toObjects(Event::class.java).sortedBy { it.date })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // Para el feed de News: eventos donde al menos uno de mis amigos es miembro.
    // whereArrayContainsAny tiene límite de 10 → dividimos en batches de 10.
    // Todas las queries se lanzan "a la vez" (no esperan entre sí) y cuando
    // todas completan (pending == 0), combinamos y devolvemos el resultado.
    fun getFriendEvents(friendIds: List<String>, onResult: (List<Event>) -> Unit) {
        val chunks    = friendIds.chunked(10)
        val collected = mutableListOf<Event>()
        var pending   = chunks.size

        chunks.forEach { chunk ->
            db.whereArrayContainsAny("memberIds", chunk)
                .get()
                .addOnSuccessListener { snapshot ->
                    synchronized(collected) {
                        collected.addAll(snapshot.toObjects(Event::class.java))
                        pending--
                        // Cuando el último batch completa, devolvemos todo sin duplicados
                        if (pending == 0) onResult(collected.distinctBy { it.id })
                    }
                }
                .addOnFailureListener {
                    synchronized(collected) {
                        pending--
                        if (pending == 0) onResult(collected.distinctBy { it.id })
                    }
                }
        }
    }

    // Consulta puntual (una sola vez, no tiempo real)
    fun getEventById(eventId: String, onResult: (Event?) -> Unit) {
        db.document(eventId).get()
            .addOnSuccessListener { onResult(it.toObject(Event::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    fun updateEventItems(eventId: String, newItems: List<EventItem>, onResult: (Boolean) -> Unit) {
        db.document(eventId).update("items", newItems)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // Solo marca el campo finished = true. No toca el resto del documento.
    fun finishEvent(eventId: String, onResult: (Boolean) -> Unit) {
        db.document(eventId).update("finished", true)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
