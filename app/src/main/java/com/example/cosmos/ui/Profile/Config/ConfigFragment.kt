package com.example.cosmos.ui.Profile.Config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.cosmos.LocaleHelper
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentConfigBinding
import com.example.cosmos.ui.LogIn.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfigViewModel by viewModels()

    // ID del usuario actual, viaja desde LoginActivity por Intent
    private val userId: String by lazy {
        activity?.intent?.getStringExtra("USER_ID") ?: ""
    }

    // Mientras se carga el usuario los switches disparan saveConfigField.
    // Este flag evita guardar durante la carga inicial.
    private var switchesReady = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initData()
        initListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initData() {
        viewModel.loadUser(userId)
    }

    private fun initListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnSaveUsername.setOnClickListener {
            hideKeyboard()
            viewModel.saveUsername(userId, binding.etUsername.text.toString())
        }

        // Cada switch guarda solo su campo correspondiente en config.*
        binding.switchPrivate.setOnCheckedChangeListener { _, checked ->
            if (switchesReady) viewModel.saveConfigField(userId, "isPrivate", checked)
        }
        binding.switchShowEmail.setOnCheckedChangeListener { _, checked ->
            if (switchesReady) viewModel.saveConfigField(userId, "showEmail", checked)
        }
        binding.switchNotifyRequests.setOnCheckedChangeListener { _, checked ->
            if (switchesReady) viewModel.saveConfigField(userId, "notifyRequests", checked)
        }
        binding.switchNotifyEvents.setOnCheckedChangeListener { _, checked ->
            if (switchesReady) viewModel.saveConfigField(userId, "notifyEvents", checked)
        }

        // ── Idioma ──
        setupLanguageChips()

        binding.cardShareInvite.setOnClickListener {
            // Genera cosmos://invite/{userId} y abre el selector de apps para compartir.
            // Quien reciba el enlace y lo abra tendrá que estar logueado en COSMOS;
            // la app enviará automáticamente una solicitud de amistad al dueño del enlace.
            val link = Uri.Builder()
                .scheme("cosmos")
                .authority("invite")
                .appendPath(userId)
                .build()
                .toString()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Seamos amigos en COSMOS: $link")
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir enlace"))
        }

        binding.cardLogout.setOnClickListener {
            requireContext()
                .getSharedPreferences("cosmos_session", Context.MODE_PRIVATE)
                .edit().clear().apply()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> handleUiState(state) } }
                launch { viewModel.saveUsernameState.collect { state -> handleSaveUsername(state) } }
            }
        }
    }

    private fun handleUiState(state: ConfigUiState) {
        when (state) {
            is ConfigUiState.Loaded -> {
                // Bloqueamos los listeners mientras llenamos los campos
                switchesReady = false
                binding.etUsername.setText(state.user.username ?: "")
                binding.switchPrivate.isChecked        = state.user.config.isPrivate
                binding.switchShowEmail.isChecked      = state.user.config.showEmail
                binding.switchNotifyRequests.isChecked = state.user.config.notifyRequests
                binding.switchNotifyEvents.isChecked   = state.user.config.notifyEvents
                switchesReady = true
            }
            else -> { /* Loading / Idle / Error: nada que mostrar por ahora */ }
        }
    }

    private fun handleSaveUsername(state: SaveUsernameState) {
        when (state) {
            is SaveUsernameState.Saving -> {
                binding.tvUsernameStatus.visibility = View.VISIBLE
                binding.tvUsernameStatus.text       = getString(R.string.saving)
                binding.tvUsernameStatus.setTextColor(0x88FFFFFF.toInt())
            }
            is SaveUsernameState.Success -> {
                binding.tvUsernameStatus.visibility = View.VISIBLE
                binding.tvUsernameStatus.text       = getString(R.string.saved)
                binding.tvUsernameStatus.setTextColor(0xFF7BC67E.toInt())
                viewModel.resetSaveUsernameState()
            }
            is SaveUsernameState.Error -> {
                binding.tvUsernameStatus.visibility = View.VISIBLE
                binding.tvUsernameStatus.text       = state.msg
                binding.tvUsernameStatus.setTextColor(0xFFCF6679.toInt())
                viewModel.resetSaveUsernameState()
            }
            is SaveUsernameState.Idle -> {
                binding.tvUsernameStatus.visibility = View.GONE
            }
        }
    }

    // ── Idioma ─────────────────────────────────────────────────────────────────

    private fun setupLanguageChips() {
        val chips = mapOf(
            "" to binding.chipLangSystem,
            "es" to binding.chipLangEs,
            "en" to binding.chipLangEn,
            "eu" to binding.chipLangEu
        )
        val current = LocaleHelper.getSavedLanguage(requireContext())
        highlightLangChip(chips, current)

        chips.forEach { (lang, chip) ->
            chip.setOnClickListener {
                highlightLangChip(chips, lang)
                applyLanguage(lang)
            }
        }
    }

    private fun highlightLangChip(chips: Map<String, TextView>, selected: String) {
        chips.forEach { (lang, chip) ->
            if (lang == selected) {
                chip.setBackgroundResource(R.drawable.bg_tab_selected)
                chip.setTextColor(0xFFC4BCFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_glass)
                chip.setTextColor(0x88FFFFFF.toInt())
            }
        }
    }

    private fun applyLanguage(lang: String) {
        // Guardar en SharedPreferences (para que esté disponible al arrancar)
        LocaleHelper.saveLanguage(requireContext(), lang)
        // Guardar en Firestore (para que se sincronice entre dispositivos)
        viewModel.saveConfigField(userId, "language", lang)
        // Recrear la activity para aplicar el nuevo idioma
        requireActivity().recreate()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
