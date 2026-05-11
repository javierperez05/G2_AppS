package com.example.cosmos.ui.Profile

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  ViewModel
 *      Clase que guarda y gestiona los datos de una pantalla.
 *      No tiene referencias a vistas (no sabe nada de botones ni layouts).
 *      Sobrevive a rotaciones de pantalla. Cuando el Fragment se destruye
 *      y se recrea, el ViewModel sigue vivo con los mismos datos.
 *
 *  StateFlow<T>
 *      Un "valor observable con memoria". Siempre tiene un valor actual y
 *      notifica a quien lo esté escuchando cuando cambia.
 *      _uiState es el MutableStateFlow (solo el ViewModel escribe en él).
 *      uiState es el StateFlow público (el Fragment solo puede leer).
 *      La diferencia de nombre (_privado / público) es intencional:
 *      nadie de fuera puede cambiar el estado directamente.
 *
 *  sealed class
 *      Una clase que solo puede tener subtipos predefinidos.
 *      Aquí modela los tres estados posibles de una pantalla:
 *        - Loading  → estamos cargando, muestra spinner
 *        - Success  → tenemos datos, muestra la UI
 *        - Error    → algo falló, muestra mensaje
 *      El compilador obliga a cubrir los tres en cualquier when(),
 *      así nunca se te olvida manejar un caso.
 *
 *  @HiltViewModel + @Inject constructor
 *      Le dicen a Hilt (el sistema de inyección de dependencias)
 *      que él es responsable de construir este ViewModel y de
 *      proporcionarle los repositorios que necesita automáticamente.
 *      Tú nunca escribes "val repo = UserRepository(firestore)".
 *
 *  tryEmit()
 *      Patrón para esperar a que varias llamadas asíncronas terminen
 *      antes de emitir el estado. Aquí cargamos usuario, eventos y
 *      órbitas en paralelo (3 llamadas a Firestore simultáneas).
 *      Cada una guarda su resultado en una variable caché y llama
 *      a tryEmit(). tryEmit() solo emite cuando los 3 ya tienen dato.
 * ═══════════════════════════════════════════════════════════════════
 */

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Event.Event
import com.example.cosmos.Model.Firestore.Repositories.EventRepository
import com.example.cosmos.Model.Firestore.Repositories.OrbitRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// Estado de la pantalla de perfil completo
sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(
        val user: User,
        val events: List<Event>,
        val orbitCount: Int
    ) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

// Estado específico de la subida de avatar (separado del estado general
// para no obligar a recargar toda la pantalla cuando solo cambia la foto)
sealed class AvatarState {
    object Idle : AvatarState()       // sin operación en curso
    object Uploading : AvatarState()  // guardando en Firestore
    object Success : AvatarState()    // guardado correctamente
    data class Error(val message: String) : AvatarState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val orbitRepository: OrbitRepository
) : ViewModel() {

    // El guion bajo (_) indica que es la versión mutable y privada.
    // Fuera del ViewModel solo se expone la versión de solo lectura (uiState).
    private val _avatarState = MutableStateFlow<AvatarState>(AvatarState.Idle)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Variables caché para el patrón tryEmit.
    // Son null hasta que cada llamada async completa.
    private var cachedUser: User? = null
    private var cachedEvents: List<Event>? = null
    private var cachedOrbitCount: Int? = null

    fun loadProfile(userId: String) {
        if (userId.isEmpty()) {
            _uiState.value = ProfileUiState.Error("Usuario no identificado")
            return
        }
        _uiState.value = ProfileUiState.Loading

        // Resetear caché para que tryEmit no emita datos de una carga anterior
        cachedUser = null
        cachedEvents = null
        cachedOrbitCount = null

        // Las 3 llamadas se lanzan casi simultáneamente (no se esperan entre sí).
        // Cada una llama a tryEmit() al terminar. La primera en llegar guarda
        // su resultado pero tryEmit() no emite todavía. Solo cuando las 3
        // han completado, tryEmit() encuentra todo distinto de null y emite.
        userRepository.getUserById(userId) { user ->
            cachedUser = user
            tryEmit()
        }

        eventRepository.getUserEvents(userId) { events ->
            cachedEvents = events
            tryEmit()
        }

        orbitRepository.getUserGroups(userId) { groups ->
            cachedOrbitCount = groups.size
            tryEmit()
        }
    }

    // El ?: return es la clave: si cualquiera de los tres todavía es null,
    // la función se interrumpe y no emite nada. Solo pasa cuando los 3 están listos.
    private fun tryEmit() {
        val user       = cachedUser       ?: return
        val events     = cachedEvents     ?: return
        val orbitCount = cachedOrbitCount ?: return
        _uiState.value = ProfileUiState.Success(user, events, orbitCount)
    }

    // Comprime la imagen a Base64 en el Fragment (necesita Context para leer la URI)
    // y aquí solo guardamos el string resultante en Firestore.
    // Después de guardar actualizamos el _uiState directamente para que el avatar
    // aparezca sin necesidad de recargar todo el perfil desde Firestore.
    fun saveAvatarBase64(userId: String, base64: String) {
        _avatarState.value = AvatarState.Uploading
        userRepository.updateUserFields(userId, mapOf("profilePictureBase64" to base64)) { success ->
            if (success) {
                _avatarState.value = AvatarState.Success
                // Actualización optimista: modificamos el objeto User en memoria
                // en vez de relanzar loadProfile() completo
                val current = _uiState.value
                if (current is ProfileUiState.Success) {
                    _uiState.value = current.copy(user = current.user.copy(profilePictureBase64 = base64))
                }
            } else {
                _avatarState.value = AvatarState.Error("Error al guardar avatar")
            }
        }
    }

    // Vuelve a Idle para que el Fragment no reprocese el mismo éxito/error
    // si se recrea (el StateFlow recuerda su último valor para siempre)
    fun resetAvatarState() { _avatarState.value = AvatarState.Idle }
}
