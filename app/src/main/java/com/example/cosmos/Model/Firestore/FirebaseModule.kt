package com.example.cosmos.Model.Firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // ── Hilt providers ────────────────────────────────────────────────────────
    // Los repositorios reciben estas instancias por constructor injection.

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage =
        FirebaseStorage.getInstance()

    // ── Acceso estático — para código que aún no está migrado ─────────────────
    // No rompe nada existente. Puedes eliminarlas cuando todo esté migrado.

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val usersCollection  get() = firestore.collection("users")
    val eventsCollection get() = firestore.collection("events")
    val groupsCollection get() = firestore.collection("groups")
}