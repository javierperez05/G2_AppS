package com.example.cosmos.ui.Orbit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentGroupBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupFragment : Fragment() {

    private var _binding: FragmentGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by viewModels()

    private var currentUserId = ""
    private var allGroups = listOf<Group>()

    private val groupAdapter = GroupAdapter { group ->
        viewModel.selectGroup(group)
        findNavController().navigate(R.id.action_groupFragment_to_groupDetailFragment)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI()
        initListeners()
        observeViewModel()
        viewModel.loadGroups(currentUserId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initUI() {
        // OrbitalLayoutManager — ajusta el package a donde lo tengas en tu proyecto
        // Si está en ui/Orbit/ usa: OrbitalLayoutManager(requireContext())
        // Si está en otro sitio ajusta el import manualmente
        binding.rvGroups.apply {
            // TODO: descomentar cuando tengas el OrbitalLayoutManager en el proyecto
            // layoutManager = OrbitalLayoutManager(requireContext())
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = groupAdapter
            itemAnimator = null
        }
    }

    private fun initListeners() {
        binding.fabNewGroup.setOnClickListener { showCreateGroupDialog() }
        binding.btnGroups.setOnClickListener { showGroupsBottomSheet() }
        binding.btnFriends.setOnClickListener { showFriendsBottomSheet() }

        binding.etSearchGroup.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                val filtered = if (query.isEmpty()) allGroups
                else allGroups.filter {
                    it.name?.contains(query, ignoreCase = true) == true
                }
                groupAdapter.submitList(filtered)
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is GroupsUiState.Loading
                        binding.layoutEmpty.isVisible  = state is GroupsUiState.Empty
                        binding.rvGroups.isVisible     = state is GroupsUiState.Success
                        if (state is GroupsUiState.Success) {
                            allGroups = state.groups
                            groupAdapter.submitList(state.groups)
                        }
                    }
                }

                launch {
                    viewModel.actionState.collect { state ->
                        when (state) {
                            is GroupActionState.Success -> viewModel.resetActionState()
                            is GroupActionState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetActionState()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun showGroupsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_groups, null)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showFriendsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_friends, null)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showCreateGroupDialog() {
        // Dialog simple sin layout personalizado — evita el problema de inflate
        val nameInput = EditText(requireContext()).apply {
            hint = "Nombre de la órbita"
            setTextColor(resources.getColor(android.R.color.white, null))
        }
        val descInput = EditText(requireContext()).apply {
            hint = "Descripción (opcional)"
            setTextColor(resources.getColor(android.R.color.white, null))
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(nameInput)
            addView(descInput)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nueva órbita")
            .setView(container)
            .setPositiveButton("Crear") { _, _ ->
                viewModel.createGroup(
                    name        = nameInput.text.toString(),
                    description = descInput.text.toString(),
                    userId      = currentUserId
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}