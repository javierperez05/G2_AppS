package com.example.cosmos.ui.LogIn.vmLogin

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Dos sealed class (LoginUiState, RegisterUiState)
 *      La pantalla tiene dos flujos independientes: login y registro.
 *      Cada uno tiene su propio estado porque ocurren en tabs distintas
 *      y no deben interferir entre sí. Si el registro falla, el estado
 *      del login no cambia.
 *
 *  Auth propia (NO Firebase Auth)
 *      La autenticación compara email+password contra documentos en la
 *      colección "users" de Firestore. loginUser() busca el documento
 *      y compara campos directamente.
 *
 *  resetLoginState() / resetRegisterState()
 *      StateFlow guarda el último valor. Si el usuario vuelve a la
 *      pantalla tras un login exitoso, el Success anterior se
 *      re-emitiría y navegaría de nuevo. Reset lo devuelve a Idle.
 * ═══════════════════════════════════════════════════════════════════
 */

import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class LoginUiState {
    object Idle    : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class RegisterUiState {
    object Idle    : RegisterUiState()
    object Loading : RegisterUiState()
    data class Success(val message: String) : RegisterUiState()
    data class Error(val message: String)   : RegisterUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    fun login(email: String, password: String) {
        _loginState.value = LoginUiState.Loading
        userRepository.loginUser(email, password) { user ->
            _loginState.value = if (user != null) LoginUiState.Success(user)
            else LoginUiState.Error("Usuario o contraseña incorrectos")
        }
    }

    fun register(username: String, email: String, password: String) {
        _registerState.value = RegisterUiState.Loading
        val newUser = User(username = username, email = email, password = password)
        userRepository.registerUser(newUser) { success, msg ->
            _registerState.value = if (success) RegisterUiState.Success(msg)
            else RegisterUiState.Error(msg)
        }
    }

    fun resetLoginState()    { _loginState.value    = LoginUiState.Idle }
    fun resetRegisterState() { _registerState.value = RegisterUiState.Idle }
}