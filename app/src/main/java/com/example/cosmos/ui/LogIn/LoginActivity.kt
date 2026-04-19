package com.example.cosmos.ui.LogIn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.ActivityLoginBinding
import com.example.cosmos.ui.HUD.NavigationHUD
import com.google.android.material.tabs.TabLayout
import kotlin.jvm.java

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Gestión de Pestañas (Tabs)
        binding.authTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.loginContainer.visibility = View.VISIBLE
                    binding.registerContainer.visibility = View.GONE
                } else {
                    binding.loginContainer.visibility = View.GONE
                    binding.registerContainer.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 2. Acción de Login
        binding.btnLogin.setOnClickListener {
            val email = binding.loginEmail.text.toString().trim()
            val pass = binding.loginPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                userRepository.loginUser(email, pass) { user ->
                    if (user != null) {
                        // Navegamos al HUD (NavigationHUD) enviando el ID
                        val intent = Intent(this, NavigationHUD::class.java).apply {
                            putExtra("USER_ID", user.id)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Usuario o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 3. Acción de Registro
        binding.btnRegister.setOnClickListener {
            val name = binding.regUsername.text.toString().trim()
            val email = binding.regEmail.text.toString().trim()
            val pass = binding.regPassword.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                val newUser = User(username = name, email = email, password = pass)
                userRepository.registerUser(newUser) { success, msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    if (success) binding.authTabLayout.getTabAt(0)?.select() // Volver a login
                }
            }
        }
    }
}