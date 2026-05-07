package com.example.cosmos.ui.LogIn

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cosmos.R
import com.example.cosmos.databinding.ActivityLoginBinding
import com.example.cosmos.ui.HUD.NavigationHUD
import com.example.cosmos.ui.LogIn.vmLogin.LoginUiState
import com.example.cosmos.ui.LogIn.vmLogin.LoginViewModel
import com.example.cosmos.ui.LogIn.vmLogin.RegisterUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login: si hay sesión guardada, saltar directo al HUD
        val prefs = getSharedPreferences("cosmos_session", MODE_PRIVATE)
        val savedUserId = prefs.getString("USER_ID", null)
        if (!savedUserId.isNullOrEmpty()) {
            startActivity(
                Intent(this, NavigationHUD::class.java).apply {
                    putExtra("USER_ID", savedUserId)
                }
            )
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initListeners()
        observeViewModel()
    }

    private fun initListeners() {
        binding.tabLogin.setOnClickListener { switchTab(0) }
        binding.tabRegister.setOnClickListener { switchTab(1) }
        binding.tvGoToRegister.setOnClickListener { switchTab(1) }

        binding.btnLogin.setOnClickListener {
            val email = binding.loginEmail.text.toString().trim()
            val pass  = binding.loginPassword.text.toString().trim()
            if (email.isNotEmpty() && pass.isNotEmpty()) {
                viewModel.login(email, pass)
            } else {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            val name  = binding.regUsername.text.toString().trim()
            val email = binding.regEmail.text.toString().trim()
            val pass  = binding.regPassword.text.toString().trim()
            if (name.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                viewModel.register(name, email, pass)
            } else {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchTab(tab: Int) {
        if (tab == 0) {
            binding.loginContainer.visibility    = View.VISIBLE
            binding.registerContainer.visibility = View.GONE
            binding.tabLogin.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabLogin.setTextColor(0xFFC4BCFF.toInt())
            binding.tabRegister.setBackgroundResource(android.R.color.transparent)
            binding.tabRegister.setTextColor(0x55FFFFFF.toInt())
        } else {
            binding.loginContainer.visibility    = View.GONE
            binding.registerContainer.visibility = View.VISIBLE
            binding.tabRegister.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabRegister.setTextColor(0xFFC4BCFF.toInt())
            binding.tabLogin.setBackgroundResource(android.R.color.transparent)
            binding.tabLogin.setTextColor(0x55FFFFFF.toInt())
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loginState.collect { state ->
                        when (state) {
                            is LoginUiState.Loading -> {
                                binding.btnLogin.isEnabled = false
                                binding.btnLogin.text = "Entrando..."
                            }
                            is LoginUiState.Success -> {
                                viewModel.resetLoginState()
                                // Guardar sesión en SharedPreferences
                                getSharedPreferences("cosmos_session", MODE_PRIVATE)
                                    .edit().putString("USER_ID", state.user.id).apply()
                                startActivity(
                                    Intent(this@LoginActivity, NavigationHUD::class.java).apply {
                                        putExtra("USER_ID", state.user.id)
                                    }
                                )
                                Log.i("LoginActivity", "Login exitoso: ${state.user.username}")
                                finish()
                            }
                            is LoginUiState.Error -> {
                                binding.btnLogin.isEnabled = true
                                binding.btnLogin.text = "Iniciar sesión"
                                viewModel.resetLoginState()
                                Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                binding.btnLogin.isEnabled = true
                                binding.btnLogin.text = "Iniciar sesión"
                            }
                        }
                    }
                }

                launch {
                    viewModel.registerState.collect { state ->
                        when (state) {
                            is RegisterUiState.Loading -> {
                                binding.btnRegister.isEnabled = false
                                binding.btnRegister.text = "Creando cuenta..."
                            }
                            is RegisterUiState.Success -> {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = "Crear cuenta"
                                viewModel.resetRegisterState()
                                Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                                switchTab(0)
                            }
                            is RegisterUiState.Error -> {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = "Crear cuenta"
                                viewModel.resetRegisterState()
                                Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = "Crear cuenta"
                            }
                        }
                    }
                }
            }
        }
    }
}