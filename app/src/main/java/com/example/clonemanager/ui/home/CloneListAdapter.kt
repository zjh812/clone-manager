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
        val binding = ItemCloneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CloneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CloneViewHolder, position: Int) {
        holder.bind(getItem(position), operating, onStart, onStop, onDelete, onManage, onRename, renameSupported)
    }

    class CloneViewHolder(private val binding: ItemCloneBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            clone: CloneProfile, operating: Boolean,
            onStart: (CloneProfile) -> Unit, onStop: (CloneProfile) -> Unit,
            onDelete: (CloneProfile) -> Unit, onManage: (CloneProfile) -> Unit,
            onRename: (CloneProfile) -> Unit, renameSupported: Boolean
        ) {
            val ctx = binding.root.context
            binding.cloneName.text = clone.name
            val running = clone.running
            binding.cloneState.text = ctx.getString(
                if (running) R.string.home_status_running else R.string.home_status_stopped)
            binding.cloneState.setTextColor(resolveColor(ctx,
                if (running) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOutline))
            binding.cloneUserId.text = ctx.getString(R.string.home_user_id_format, clone.userId)
            val type = clone.userType
            if (type.isNullOrEmpty()) binding.cloneType.visibility = View.GONE
            else { binding.cloneType.visibility = View.VISIBLE; binding.cloneType.text = type }
            binding.btnStart.visibility = if (running) View.GONE else View.VISIBLE
            binding.btnStop.visibility = if (running) View.VISIBLE else View.GONE
            binding.btnStart.isEnabled = !operating
            binding.btnStop.isEnabled = !operating
            binding.btnStart.setOnClickListener { onStart(clone) }
            binding.btnStop.setOnClickListener { onStop(clone) }
            binding.btnDelete.setOnClickListener { onDelete(clone) }
            binding.btnDelete.isEnabled = !operating
            binding.btnManage.setOnClickListener { onManage(clone) }
            binding.btnManage.isEnabled = !operating
            binding.btnRename.setOnClickListener { onRename(clone) }
            binding.btnRename.visibility = if (renameSupported) View.VISIBLE else View.GONE
            binding.btnRename.isEnabled = !operating
        }

        private fun resolveColor(ctx: android.content.Context, attr: Int): Int {
            val ta = ctx.obtainStyledAttributes(intArrayOf(attr))
            val color = ta.getColor(0, 0xFF888888.toInt()); ta.recycle(); return color
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<CloneProfile>() {
            override fun areItemsTheSame(o: CloneProfile, n: CloneProfile) = o.userId == n.userId
            override fun areContentsTheSame(o: CloneProfile, n: CloneProfile) = o == n
        }
    }
}
