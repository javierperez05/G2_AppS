package com.example.cosmos.ui.Events.Detail

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmos.Model.Event.ForumThread
import com.example.cosmos.R
import com.example.cosmos.databinding.ItemForumThreadBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForumThreadAdapter(
    private val onReplyClick: (ForumThread) -> Unit
) : ListAdapter<ForumThread, ForumThreadAdapter.ThreadViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<ForumThread>() {
        override fun areItemsTheSame(a: ForumThread, b: ForumThread) = a.id == b.id
        override fun areContentsTheSame(a: ForumThread, b: ForumThread) = a == b
    }

    inner class ThreadViewHolder(val binding: ItemForumThreadBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ThreadViewHolder(
            ItemForumThreadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        val thread = getItem(position)
        val b = holder.binding
        val fmt = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())

        b.tvThreadAuthor.text = thread.authorName.ifBlank { "Anon" }
        b.tvThreadText.text = thread.text
        b.tvThreadTime.text = fmt.format(Date(thread.createdAt))

        // Poblar replies dinamicamente
        b.llReplies.removeAllViews()
        if (thread.replies.isNotEmpty()) {
            b.dividerReplies.isVisible = true
            thread.replies.forEach { reply ->
                val replyView = LayoutInflater.from(b.root.context)
                    .inflate(R.layout.item_forum_reply, b.llReplies, false)
                replyView.findViewById<TextView>(R.id.tvReplyAuthor).text =
                    reply.authorName.ifBlank { "Anon" }
                replyView.findViewById<TextView>(R.id.tvReplyText).text = reply.text
                replyView.findViewById<TextView>(R.id.tvReplyTime).text =
                    fmt.format(Date(reply.createdAt))
                b.llReplies.addView(replyView)
            }
        } else {
            b.dividerReplies.isVisible = false
        }

        b.btnReply.setOnClickListener { onReplyClick(thread) }
    }
}
