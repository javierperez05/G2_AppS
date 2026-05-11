package com.example.cosmos.ui.Profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentProfileBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private var currentUserId = ""

    private val eventAdapter = ProfileEventAdapter { event ->
        val bundle = android.os.Bundle().apply { putString("eventId", event.id ?: "") }
        findNavController().navigate(R.id.action_profileFragment_to_eventDetailFragment, bundle)
    }

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val base64 = compressToBase64(uri) ?: return@registerForActivityResult
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            Glide.with(this).load(bytes).circleCrop().into(binding.ivProfileAvatar)
            viewModel.saveAvatarBase64(currentUserId, base64)
        }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI()
        initListeners()
        observeViewModel()
        viewModel.loadProfile(currentUserId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private fun initUI() {
        binding.rvProfileEvents.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = eventAdapter
            itemAnimator = null
        }
    }

    private fun initListeners() {
        binding.btnProfileConfig.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_configFragment)
        }
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_configFragment)
        }
        binding.ivProfileAvatar.setOnClickListener {
            pickAvatar.launch("image/*")
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state is ProfileUiState.Success) {
                            binding.tvProfileUsername.text = state.user.username ?: "Usuario"
                            binding.tvProfileBio.text = state.user.email ?: ""
                            binding.tvProfileEventCount.text = state.events.size.toString()
                            binding.tvProfileFriendCount.text = state.user.friends.size.toString()
                            binding.tvProfileOrbitCount.text = state.orbitCount.toString()
                            binding.layoutProfileEmpty.isVisible = state.events.isEmpty()
                            binding.rvProfileEvents.isVisible = state.events.isNotEmpty()
                            if (state.events.isNotEmpty()) eventAdapter.submitList(state.events)

                            loadAvatar(state.user.profilePictureBase64)
                        }
                    }
                }
                launch {
                    viewModel.avatarState.collect { state ->
                        when (state) {
                            is AvatarState.Uploading -> {
                                Snackbar.make(binding.root, "Guardando avatar...", Snackbar.LENGTH_SHORT).show()
                            }
                            is AvatarState.Success -> {
                                Snackbar.make(binding.root, "Avatar actualizado", Snackbar.LENGTH_SHORT).show()
                                viewModel.resetAvatarState()
                            }
                            is AvatarState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetAvatarState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    // ── Avatar ────────────────────────────────────────────────────────────────

    private fun loadAvatar(base64: String?) {
        if (!base64.isNullOrEmpty()) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            Glide.with(this)
                .load(bytes)
                .circleCrop()
                .placeholder(R.drawable.ic_cosmos_critter)
                .into(binding.ivProfileAvatar)
        } else {
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_cosmos_critter)
        }
    }

    private fun compressToBase64(uri: android.net.Uri): String? = try {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        val scaled = Bitmap.createScaledBitmap(original, 150, 150, true)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { null }
}
