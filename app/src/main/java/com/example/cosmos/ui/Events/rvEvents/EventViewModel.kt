package com.example.cosmos.ui.Events.rvEvents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.EventRepository

class EventViewModel : ViewModel() {
    private val repository = EventRepository() // Tu repositorio de Firestore

    // Aquí guardaremos la lista de eventos que viene de la DB
    private val _events = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> get() = _events

    // Estado de carga (para mostrar un spinner si quieres)
    val isLoading = MutableLiveData<Boolean>()

    fun fetchEvents(userId: String) {
        isLoading.value = true
        repository.getEventsForUser(userId) { listaDeEventos ->
            _events.postValue(listaDeEventos)
            isLoading.postValue(false)
        }
    }
}