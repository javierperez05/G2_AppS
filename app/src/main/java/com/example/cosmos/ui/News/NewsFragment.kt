package com.example.cosmos.ui.News

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.cosmos.Model.Actions.Post
import com.example.cosmos.Model.Users.Group
import com.example.cosmos.Model.Users.User
import com.example.cosmos.R
import com.example.cosmos.databinding.FragmentNewsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModels()

    private lateinit var newsAdapter: NewsPostAdapter
    var currentUserId = ""

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentUserId = activity?.intent?.getStringExtra("USER_ID") ?: ""
        initUI()
        initListeners()
        observeViewModel()
        viewModel.loadNews(currentUserId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    fun navigateToEvent(eventId: String) {
        val bundle = Bundle().apply {
            putString("eventId", eventId)
            putBoolean("readOnly", true)
        }
        findNavController().navigate(R.id.action_newsFragment_to_eventDetailFragment, bundle)
    }

    private fun initUI() {
        newsAdapter = NewsPostAdapter(
            currentUserId = currentUserId,
            memberNames = emptyMap(),
            onCrewClick = { post ->
                viewModel.loadCrewRates(post.eventId ?: "", post.memberIds)
            },
            onViewEvent = { eventId -> navigateToEvent(eventId) },
            onProposeClick = { post ->
                viewModel.loadProposeData(currentUserId, post)
            },
            onCardClick = { post ->
                viewModel.selectPost(post)
                PostDetailBottomSheet().show(childFragmentManager, "post_detail")
            }
        )
        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }
    }

    private fun initListeners() {
        binding.overlayRateDetail.overlayRoot.setOnClickListener {
            binding.overlayRateDetail.overlayRoot.isVisible = false
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is NewsUiState.Loading
                        binding.layoutEmpty.isVisible = state is NewsUiState.Empty
                        binding.rvNews.isVisible = state is NewsUiState.Success

                        if (state is NewsUiState.Success) {
                            newsAdapter = NewsPostAdapter(
                                currentUserId = currentUserId,
                                memberNames = state.memberNames,
                                avatarUrls = state.avatarUrls,
                                onCrewClick = { post ->
                                    viewModel.loadCrewRates(post.eventId ?: "", post.memberIds)
                                },
                                onViewEvent = { eventId -> navigateToEvent(eventId) },
                                onProposeClick = { post ->
                                    viewModel.loadProposeData(currentUserId, post)
                                },
                                onCardClick = { post ->
                                    viewModel.selectPost(post)
                                    PostDetailBottomSheet().show(childFragmentManager, "post_detail")
                                }
                            )
                            binding.rvNews.adapter = newsAdapter
                            newsAdapter.submitList(state.posts)
                        }
                    }
                }
                launch {
                    viewModel.crewState.collect { state ->
                        if (state is CrewUiState.Ready) {
                            showCrewBottomSheet(state)
                            viewModel.resetCrewState()
                        }
                    }
                }
                launch {
                    viewModel.proposeState.collect { state ->
                        when (state) {
                            is ProposeUiState.Ready -> showProposeSheet(state)
                            is ProposeUiState.Proposed -> {
                                Snackbar.make(binding.root, "¡Misión propuesta!", Snackbar.LENGTH_SHORT).show()
                                viewModel.resetProposeState()
                            }
                            is ProposeUiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetProposeState()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // ── Bottom sheet de tripulacion ──────────────────────────────────────────

    private fun showCrewBottomSheet(state: CrewUiState.Ready) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_crew_rates, null)
        dialog.setContentView(sheetView)

        val rv = sheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvCrewRates)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val crewItems = state.memberIds.map { memberId ->
            CrewRateItem(
                userId = memberId,
                username = state.memberNames[memberId] ?: "?",
                rate = state.rates.find { it.userId == memberId },
                avatarUrl = state.avatarUrls[memberId]
            )
        }

        rv.adapter = CrewRateAdapter(crewItems) { item ->
            dialog.dismiss()
            showRateOverlay(item)
        }

        dialog.show()
    }

    // ── Overlay de rate individual ──────────────────────────────────────────

    private fun showRateOverlay(item: CrewRateItem) {
        val overlay = binding.overlayRateDetail
        overlay.overlayRoot.isVisible = true

        overlay.tvOverlayUsername.text = "@${item.username}"

        if (!item.avatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(item.avatarUrl).circleCrop()
                .placeholder(R.drawable.ic_circle_profile).into(overlay.ivOverlayAvatar)
        } else {
            overlay.ivOverlayAvatar.setImageResource(R.drawable.ic_circle_profile)
        }

        if (item.rate != null) {
            val filled = item.rate.rating.toInt().coerceIn(0, 5)
            overlay.tvOverlayStars.text = "\u2605".repeat(filled) + "\u2606".repeat(5 - filled)
            overlay.tvOverlayStars.setTextColor(0xFFFFD700.toInt())
            overlay.tvOverlayRating.text = "%.1f / 5.0".format(item.rate.rating)
            overlay.tvOverlayRating.isVisible = true
            overlay.tvOverlayComment.text = item.rate.comment ?: ""
            overlay.tvOverlayComment.isVisible = !item.rate.comment.isNullOrBlank()
            overlay.tvOverlayNoRate.isVisible = false
        } else {
            overlay.tvOverlayStars.text = ""
            overlay.tvOverlayRating.isVisible = false
            overlay.tvOverlayComment.isVisible = false
            overlay.tvOverlayNoRate.isVisible = true
        }
    }

    // ── Bottom sheet proponer ─────────────────────────────────────────────────

    private fun showProposeSheet(state: ProposeUiState.Ready) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottomsheet_propose, null)
        dialog.setContentView(sheetView)

        val tvEventName      = sheetView.findViewById<TextView>(R.id.tvProposeEventName)
        val chipGroups       = sheetView.findViewById<TextView>(R.id.chipProposeGroups)
        val chipAmigos       = sheetView.findViewById<TextView>(R.id.chipProposeAmigos)
        val progressPropose  = sheetView.findViewById<View>(R.id.progressPropose)
        val tvEmpty          = sheetView.findViewById<TextView>(R.id.tvProposeEmpty)
        val llItems          = sheetView.findViewById<LinearLayout>(R.id.llProposeItems)

        tvEventName.text = state.post.eventTitle ?: ""

        var showingGroups = true

        fun buildRows(groups: List<Group>, friends: List<User>) {
            llItems.removeAllViews()
            val ctx = requireContext()
            val density = resources.displayMetrics.density
            val rowH = (52 * density).toInt()
            val padH = (24 * density).toInt()
            val padV = (0 * density).toInt()

            if (showingGroups) {
                tvEmpty.text = getString(R.string.no_orbits_yet)
                tvEmpty.isVisible = groups.isEmpty()
                groups.forEach { group ->
                    val row = TextView(ctx).apply {
                        text = group.name ?: getString(R.string.orbit_fallback)
                        setTextColor(0xEEFFFFFF.toInt())
                        textSize = 14f
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(padH, padV, padH, padV)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, rowH
                        )
                        setBackgroundResource(android.R.color.transparent)
                    }
                    row.setOnClickListener {
                        dialog.dismiss()
                        viewModel.proposeToGroup(state.post, group, currentUserId)
                    }
                    llItems.addView(row)
                }
            } else {
                tvEmpty.text = getString(R.string.no_friends_yet)
                tvEmpty.isVisible = friends.isEmpty()
                friends.forEach { friend ->
                    val row = TextView(ctx).apply {
                        text = "@${friend.username ?: "?"}"
                        setTextColor(0xEEFFFFFF.toInt())
                        textSize = 14f
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(padH, padV, padH, padV)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, rowH
                        )
                        setBackgroundResource(android.R.color.transparent)
                    }
                    row.setOnClickListener {
                        dialog.dismiss()
                        viewModel.proposeToFriend(state.post, friend, currentUserId)
                    }
                    llItems.addView(row)
                }
            }
        }

        fun setTab(groups: Boolean) {
            showingGroups = groups
            if (groups) {
                chipGroups.setBackgroundResource(R.drawable.bg_tab_selected)
                chipGroups.setTextColor(0xFFC4BCFF.toInt())
                chipAmigos.setBackgroundResource(R.drawable.bg_chip_glass)
                chipAmigos.setTextColor(0x88FFFFFF.toInt())
            } else {
                chipAmigos.setBackgroundResource(R.drawable.bg_tab_selected)
                chipAmigos.setTextColor(0xFFC4BCFF.toInt())
                chipGroups.setBackgroundResource(R.drawable.bg_chip_glass)
                chipGroups.setTextColor(0x88FFFFFF.toInt())
            }
            buildRows(state.groups, state.friends)
        }

        progressPropose.isVisible = false
        chipGroups.setOnClickListener { setTab(true) }
        chipAmigos.setOnClickListener { setTab(false) }
        setTab(true)

        // Re-observe proposeState for loading/error while sheet is open
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.proposeState.collect { s ->
                progressPropose.isVisible = s is ProposeUiState.Loading
            }
        }

        dialog.show()
    }
}
