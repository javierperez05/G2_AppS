package com.example.cosmos.ui.LogIn.vmLogin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User

class LoginViewModel : ViewModel() {
    private val repository = UserRepository()

    private val _userState = MutableLiveData<User?>()
    val userState: LiveData<User?> = _userState

    fun login(email: String, pass: String) {
        repository.loginUser(email, pass) { user ->
            _userState.postValue(user)
        }
    }
}