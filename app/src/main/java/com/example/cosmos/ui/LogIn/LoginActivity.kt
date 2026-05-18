package com.example.cosmos.ui.LogIn

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Auto-login con SharedPreferences
 *      Al hacer login exitoso guardamos USER_ID en "cosmos_session".
 *      Cuando la Activity se abre, si ya hay un ID guardado saltamos
 *      directo a NavigationHUD sin mostrar la pantalla de login.
 *      Cerrar sesión (ConfigFragment) hace prefs.clear() para borrar
 *      el ID y forzar el login la próxima vez.
 *
 *  Tabs custom (no TabLayout de Material)
 *      El login y registro se muestran en la misma Activity. Dos
 *      TextViews hacen de tabs: switchTab(0) muestra login,
 *      switchTab(1) muestra registro. Se cambian estilos manualmente.
 *
 *  LocaleHelper.applyLocale en attachBaseContext
 *      Se aplica el idioma guardado ANTES de que Android cree las
 *      vistas. Así los strings.xml del idioma correcto se cargan
 *      desde el principio. Todas las Activities deben hacer esto.
 *
 *  lifecycleScope + repeatOnLifecycle
 *      En Activities se usa lifecycleScope directamente (no
 *      viewLifecycleOwner como en Fragments) porque la Activity
 *      tiene un solo ciclo de vida.
 * ═══════════════════════════════════════════════════════════════════
 */

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
import android.content.Context
import com.example.cosmos.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

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
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            val name  = binding.regUsername.text.toString().trim()
            val email = binding.regEmail.text.toString().trim()
            val pass  = binding.regPassword.text.toString().trim()
            if (name.isNotEmpty() && email.isNotEmpty() && pass.isNotEmpty()) {
                viewModel.register(name, email, pass)
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
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
                                binding.btnLogin.text = getString(R.string.logging_in)
                            }
                            is LoginUiState.Success -> {
                                viewModel.resetLoginState()
                                // Guardar sesión en SharedPreferences
                                getSharedPreferences("cosmos_session", MODE_PRIVATE)
                                    .edit().putString("USER_ID", state.user.id).apply()
                                // Sincronizar idioma del usuario
                                val userLang = state.user.config.language
                                LocaleHelper.saveLanguage(this@LoginActivity, userLang)
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
                                binding.btnLogin.text = getString(R.string.btn_login)
                                viewModel.resetLoginState()
                                Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                binding.btnLogin.isEnabled = true
                                binding.btnLogin.text = getString(R.string.btn_login)
                            }
                        }
                    }
                }

                launch {
                    viewModel.registerState.collect { state ->
                        when (state) {
                            is RegisterUiState.Loading -> {
                                binding.btnRegister.isEnabled = false
                                binding.btnRegister.text = getString(R.string.registering)
                            }
                            is RegisterUiState.Success -> {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = getString(R.string.btn_register)
                                viewModel.resetRegisterState()
                                Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                                switchTab(0)
                            }
                            is RegisterUiState.Error -> {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = getString(R.string.btn_register)
                                viewModel.resetRegisterState()
                                Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                binding.btnRegister.isEnabled = true
                                binding.btnRegister.text = getString(R.string.btn_register)
                            }
                        }
                    }
                }
            }
        }
    }
}