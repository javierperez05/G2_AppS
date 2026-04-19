package com.example.cosmos.Model.Firestore

object FirebaseModule {
    val firestore by lazy {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    }

    // Colecciones de COSMOS
    val usersCollection = firestore.collection("users")
    val eventsCollection = firestore.collection("events")
    val groupsCollection = firestore.collection("groups")
}