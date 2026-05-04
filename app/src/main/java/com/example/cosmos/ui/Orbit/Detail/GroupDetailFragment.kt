package com.example.cosmos.ui.Orbit.Detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cosmos.databinding.FragmentGroupDetailBinding
import com.example.cosmos.ui.Orbit.GroupViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupDetailFragment : Fragment() {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    // Comparte el ViewModel con GroupFragment — mismo backstack
    private val viewModel: GroupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: observar viewModel.selectedGroup y rellenar la UI
        // cuando tengamos el layout del detalle completo
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}