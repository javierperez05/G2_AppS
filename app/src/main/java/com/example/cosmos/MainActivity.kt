package com.example.cosmos

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Context
import com.example.cosmos.ui.LogIn.LoginActivity


class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Si la app se abrió desde un deep link cosmos://invite/{userId},
        // guardamos el ID del invitante en SharedPreferences para procesarlo
        // después de que el usuario esté autenticado (en NavigationHUD).
        val inviteUserId = intent.data?.let { uri ->
            if (uri.scheme == "cosmos" && uri.host == "invite") uri.lastPathSegment else null
        }
        if (!inviteUserId.isNullOrEmpty()) {
            getSharedPreferences("cosmos_session", MODE_PRIVATE)
                .edit().putString("PENDING_INVITE", inviteUserId).apply()
        }

        startActivity(Intent(this, LoginActivity::class.java))
    }
}