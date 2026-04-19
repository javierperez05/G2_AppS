package com.example.cosmos.ui.Events.rvEvents

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentCreateEventBinding

class CreateEventFragment : Fragment() {
    private var _binding: FragmentCreateEventBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EventViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recuperamos el ID del usuario logueado (que viene de la Activity)
        val currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val desc = binding.etDescription.text.toString()
            val category = binding.spinnerCategory.selectedItem.toString()

            if (title.isNotEmpty() && desc.isNotEmpty()) {
                val newEvent = Event(
                    name = title,
                    description = desc,
                    category = category,
                    ownerId = currentUserId,
                    members = listOf(currentUserId), // El creador entra directo
                    date = binding.tvSelectedDate.text.toString()
                )

                viewModel.createNewEvent(newEvent) { success ->
                    if (success) {
                        Toast.makeText(context, "¡Evento estelar creado!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack() // Volver a la lista
                    }
                }
            }
        }

        // Setup del DatePicker
        binding.btnSelectDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("¿Cuándo es el evento?")
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            binding.tvSelectedDate.text = datePicker.headerText
        }
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }
}