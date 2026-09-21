package com.example.clonemanager.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.clonemanager.R
import com.example.clonemanager.data.CloneProfile
import com.example.clonemanager.databinding.ItemCloneBinding

class CloneListAdapter(
    private val onStart: (CloneProfile) -> Unit,
    private val onStop: (CloneProfile) -> Unit,
    private val onDelete: (CloneProfile) -> Unit,
    private val onManage: (CloneProfile) -> Unit,
    private val onRename: (CloneProfile) -> Unit
) : ListAdapter<CloneProfile, CloneListAdapter.CloneViewHolder>(Diff) {

    var operating: Boolean = false
        set(value) { field = value; notifyItemRangeChanged(0, itemCount) }

    var renameSupported: Boolean = true
        set(value) { field = value; notifyItemRangeChanged(0, itemCount) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CloneViewHolder {
        val b = ItemCloneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CloneViewHolder(b)
    }

    override fun onBindViewHolder(h: CloneViewHolder, p: Int) {
        h.bind(getItem(p), operating, onStart, onStop, onDelete, onManage, onRename, renameSupported)
    }

    class CloneViewHolder(private val b: ItemCloneBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(
            clone: CloneProfile, operating: Boolean,
            onStart: (CloneProfile) -> Unit, onStop: (CloneProfile) -> Unit,
            onDelete: (CloneProfile) -> Unit, onManage: (CloneProfile) -> Unit,
            onRename: (CloneProfile) -> Unit, renameSupported: Boolean
        ) {
            val ctx = b.root.context
            b.cloneName.text = clone.name
            b.cloneAvatar.text = clone.name.trim().firstOrNull()?.toString() ?: "分"

            val running = clone.running
            b.cloneState.text = ctx.getString(
                if (running) R.string.home_status_running else R.string.home_status_stopped
            )
            b.cloneState.setBackgroundResource(
                if (running) R.drawable.bg_pill_running else R.drawable.bg_pill_stopped
            )
            b.cloneState.setTextColor(
                ctx.getColor(if (running) R.color.status_running_fg else R.color.status_stopped_fg)
            )
            b.cloneUserId.text = ctx.getString(R.string.home_user_id_format, clone.userId)

            b.btnStart.visibility = if (running) View.GONE else View.VISIBLE
            b.btnStop.visibility = if (running) View.VISIBLE else View.GONE
            b.btnStart.isEnabled = !operating
            b.btnStop.isEnabled = !operating
            b.btnStart.setOnClickListener { onStart(clone) }
            b.btnStop.setOnClickListener { onStop(clone) }
            b.btnDelete.setOnClickListener { onDelete(clone) }
            b.btnDelete.isEnabled = !operating
            b.btnManage.setOnClickListener { onManage(clone) }
            b.btnManage.isEnabled = !operating
            b.btnRename.setOnClickListener { onRename(clone) }
            b.btnRename.visibility = if (renameSupported) View.VISIBLE else View.GONE
            b.btnRename.isEnabled = !operating
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<CloneProfile>() {
            override fun areItemsTheSame(o: CloneProfile, n: CloneProfile) = o.userId == n.userId
            override fun areContentsTheSame(o: CloneProfile, n: CloneProfile) = o == n
        }
    }
}
