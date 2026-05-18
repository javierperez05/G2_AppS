package com.example.cosmos.Model.Firestore

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Módulo Hilt (@Module + @InstallIn)
 *      Declara cómo Hilt debe construir las dependencias que no puede
 *      crear solo (porque son de librerías externas como Firebase).
 *      InstallIn(SingletonComponent) = viven toda la vida de la app.
 *
 *  @Provides + @Singleton
 *      Cada función @Provides enseña a Hilt a crear una instancia.
 *      @Singleton garantiza que solo se crea UNA instancia y se
 *      reutiliza en toda la app. Sin @Singleton, cada inyección
 *      crearía una nueva instancia (innecesario para Firebase).
 *
 *  Acceso estático (firestore, usersCollection, etc.)
 *      Propiedades companion para código legacy que aún no usa Hilt.
 *      Se pueden eliminar cuando toda la app use inyección.
 * ═══════════════════════════════════════════════════════════════════
 */

import com.google.firebase.firestore.FirebaseFirestore
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



    // ── Acceso estático — para código que aún no está migrado ─────────────────
    // No rompe nada existente. Puedes eliminarlas cuando todo esté migrado.

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val usersCollection  get() = firestore.collection("users")
    val eventsCollection get() = firestore.collection("events")
    val groupsCollection get() = firestore.collection("groups")
}