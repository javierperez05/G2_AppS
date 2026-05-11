package com.example.cosmos.ui.Orbit

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmos.Model.Firestore.Repositories.UserRepository
import com.example.cosmos.Model.Users.User
import com.example.cosmos.databinding.FragmentCreateGroupBinding
import com.example.cosmos.ui.Events.rvEvents.SelectedMembersAdapter
import com.example.cosmos.ui.Events.rvEvents.UserSearchAdapter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateGroupFragment : Fragment() {

    private var _binding: FragmentCreateGroupBinding? = null
    private val binding get() = _binding!!

    private val groupViewModel: GroupViewModel by activityViewModels()

    @Inject
    lateinit var userRepository: UserRepository

    private var currentUserId = ""
    private var selectedImageUri: Uri? = null
    private val selectedMemberIds = mutableListOf<String>()
    private val selectedMembers = mutableListOf<User>()

    private lateinit var searchAdapter: UserSearchAdapter
    private lateinit var membersAdapter: SelectedMembersAdapter

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivGroupImage.apply {
                setImageURI(uri)
                setPadding(0, 0, 0, 0)
                imageTintList = null
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initData()
        initUI()
        initListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initData() {
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        if (currentUserId.isNotEmpty()) {
            selectedMemberIds.add(currentUserId)
        }
    }

    private fun initUI() {
        searchAdapter = UserSearchAdapter(emptyList()) { user ->
            addMember(user)
        }
        binding.rvSearchSuggestions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        membersAdapter = SelectedMembersAdapter(selectedMembers) { user ->
            removeMember(user)
        }
        binding.rvSelectedMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = membersAdapter
        }
    }

    private fun initListeners() {
        binding.ivGroupImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.etSearchMember.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                buscarUsuarios(s.toString().trim())
            }
        })

        binding.btnCreateGroup.setOnClickListener { saveGroup() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                groupViewModel.actionState.collect { state ->
                    when (state) {
                        is GroupActionState.Loading -> {
                            binding.btnCreateGroup.isEnabled = false
                            binding.btnCreateGroup.text = "Lanzando..."
                        }
                        is GroupActionState.Success -> {
                            groupViewModel.resetActionState()
                            findNavController().popBackStack()
                        }
                        is GroupActionState.Error -> {
                            binding.btnCreateGroup.isEnabled = true
                            binding.btnCreateGroup.text = "LANZAR ORBITA"
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            groupViewModel.resetActionState()
                        }
                        else -> {
                            binding.btnCreateGroup.isEnabled = true
                            binding.btnCreateGroup.text = "LANZAR ORBITA"
                        }
                    }
                }
            }
        }
    }

    private fun buscarUsuarios(query: String) {
        if (query.isEmpty()) {
            searchAdapter.updateList(emptyList())
            binding.rvSearchSuggestions.isVisible = false
            return
        }
        userRepository.searchByUsername(query, currentUserId) { users ->
            val filtered = users.filter { !selectedMemberIds.contains(it.id) }
            searchAdapter.updateList(filtered)
            binding.rvSearchSuggestions.isVisible = filtered.isNotEmpty()
        }
    }

    private fun addMember(user: User) {
        if (!selectedMemberIds.contains(user.id)) {
            user.id?.let { selectedMemberIds.add(it) }
            selectedMembers.add(user)
            membersAdapter.notifyItemInserted(selectedMembers.size - 1)
            binding.etSearchMember.text.clear()
            binding.rvSearchSuggestions.isVisible = false
        }
    }

    private fun removeMember(user: User) {
        val index = selectedMembers.indexOf(user)
        if (index != -1) {
            selectedMemberIds.remove(user.id)
            selectedMembers.removeAt(index)
            membersAdapter.notifyItemRemoved(index)
        }
    }

    private fun saveGroup() {
        val name = binding.etGroupName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etGroupName.error = "El nombre es obligatorio"
            return
        }
        groupViewModel.createGroup(
            name = name,
            description = binding.etGroupDescription.text.toString().trim(),
            userId = currentUserId,
            memberIds = selectedMemberIds.distinct(),
            imageUri = selectedImageUri
        )
    }
}
