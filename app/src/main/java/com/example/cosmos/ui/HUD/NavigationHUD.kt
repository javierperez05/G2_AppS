package com.example.cosmos.ui.HUD

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.cosmos.Model.Actions.FriendRequest
import com.example.cosmos.Model.Firestore.Repositories.FriendRequestRepository
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.R
import android.content.Context
import com.example.cosmos.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NavigationHUD : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var binding: com.example.cosmos.databinding.ActivityNavigationHudBinding
    private lateinit var navController: NavController

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var friendRequestRepository: FriendRequestRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = com.example.cosmos.databinding.ActivityNavigationHudBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initUI()
        processPendingInvite()
    }

    private fun initUI() {
        initNavigation()
    }

    private fun initNavigation() {
        val navHost: NavHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        navController = navHost.navController
        binding.bottomMenu.setupWithNavController(navController)
    }

    // Comprueba si la app se abrió desde un enlace cosmos://invite/{userId}.
    // Si hay un invite pendiente en SharedPreferences (guardado por MainActivity),
    // carga el perfil del usuario actual para obtener su username y envía
    // automáticamente una solicitud de amistad al dueño del enlace.
    // El invite se borra de prefs inmediatamente para que no se reenvíe si
    // el usuario rota la pantalla o vuelve a abrir la app.
    private fun processPendingInvite() {
        val prefs = getSharedPreferences("cosmos_session", MODE_PRIVATE)
        val inviteToId = prefs.getString("PENDING_INVITE", null) ?: return
        val myUserId   = intent.getStringExtra("USER_ID") ?: return

        prefs.edit().remove("PENDING_INVITE").apply()

        // No tiene sentido enviarse una solicitud a uno mismo
        if (inviteToId == myUserId) return

        userRepository.getUserById(myUserId) { me ->
            if (me == null) return@getUserById
            val request = FriendRequest(
                fromId       = myUserId,
                toId         = inviteToId,
                fromUsername = me.username ?: ""
            )
            friendRequestRepository.sendRequest(request) { success ->
                val msg = if (success) getString(R.string.friend_request_sent)
                          else getString(R.string.error_request_exists)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
