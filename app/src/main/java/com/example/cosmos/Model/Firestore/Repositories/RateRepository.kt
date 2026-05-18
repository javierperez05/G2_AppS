package com.example.cosmos.Model.Firestore.Repositories

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Subcolección: events/{eventId}/rates/{userId}
 *      El userId se usa como ID del documento. Esto garantiza una
 *      sola valoración por usuario por evento: un segundo set()
 *      sobreescribe el anterior en vez de crear un duplicado.
 *      Si usáramos IDs autogenerados habría que comprobar duplicados
 *      manualmente.
 * ═══════════════════════════════════════════════════════════════════
 */

import com.example.cosmos.Model.Actions.Rate
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun ratesRef(eventId: String) =
        firestore.collection("events").document(eventId).collection("rates")

    fun getRates(eventId: String, onResult: (List<Rate>) -> Unit) {
        ratesRef(eventId).get()
            .addOnSuccessListener { onResult(it.toObjects(Rate::class.java)) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // El documento usa userId como ID para garantizar una sola rate por usuario/evento
    fun postRate(eventId: String, rate: Rate, onResult: (Boolean) -> Unit) {
        val userId = rate.userId ?: return onResult(false)
        ratesRef(eventId).document(userId).set(rate)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
