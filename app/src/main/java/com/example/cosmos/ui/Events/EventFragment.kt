package com.example.cosmos.ui.Events

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentEventBinding
import com.example.cosmos.ui.Events.rvEvents.EventAdapter
import com.example.cosmos.ui.Events.rvEvents.EventViewModel

class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EventViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        initData()
    }

    private fun initUI() {
        // Configuramos el clic para ir a la pantalla de creación

        binding.btnCreateEvent.setOnClickListener {
            // Navegamos al fragmento de creación usando el Navigation Component
            findNavController().navigate(R.id.action_profileFragment_to_configFragment)
        }
    }

    private fun initData() {
        // 1. Configurar el RecyclerView
        val adapter = EventAdapter(emptyList())
        binding.rvEvents.adapter = adapter
        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())

        // 2. Observar el LiveData
        viewModel.events.observe(viewLifecycleOwner) { listaDeEventos ->
            adapter.updateData(listaDeEventos)
        }

        // 3. Obtener el ID del usuario logueado desde la Activity
        val userId = activity?.intent?.getStringExtra("USER_ID") ?: ""

        if (userId.isNotEmpty()) {
            viewModel.fetchEvents(userId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}