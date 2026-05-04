package com.example.cosmos.ui.LogIn.vmLogin

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