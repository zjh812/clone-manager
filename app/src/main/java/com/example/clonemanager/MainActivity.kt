package com.example.clonemanager

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.clonemanager.data.CapabilityInfo
import com.example.clonemanager.databinding.ActivityMainBinding
import com.example.clonemanager.databinding.DialogCompatibilityBinding
import com.example.clonemanager.ui.logs.LogsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var cloneAdapter: com.example.clonemanager.ui.home.CloneListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        cloneAdapter = com.example.clonemanager.ui.home.CloneListAdapter(
            onStart = { viewModel.startClone(it) },
            onStop = { viewModel.stopClone(it) },
            onDelete = { askDeleteClone(it) },
            onManage = { openDetail(it) },
            onRename = { askRenameClone(it) }
        )
        binding.cloneList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.cloneList.adapter = cloneAdapter

        binding.btnCreate.setOnClickListener { askCreateClone() }
        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnCompatibility.setOnClickListener { showCompatibility() }
        binding.btnLogs.setOnClickListener { startActivity(Intent(this, LogsActivity::class.java)) }

        viewModel.uiState.observe(this) { render(it) }
        viewModel.events.observe(this) { showOpResult(it) }

        viewModel.refresh()
    }

    private fun askCreateClone() {
        val input = android.widget.EditText(this).apply { hint = "分身名称，例如：分身7" }
        MaterialAlertDialogBuilder(this)
            .setTitle("创建分身")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createClone(name)
            }
            .show()
    }

    private fun askDeleteClone(profile: com.example.clonemanager.data.CloneProfile) {
        if (profile.userId == 0) return
        val edit = android.widget.EditText(this).apply { hint = "输入分身名称以确认" }
        MaterialAlertDialogBuilder(this)
            .setTitle("删除「${profile.name}」？")
            .setMessage("User ID：${profile.userId}\n\n此操作会删除该分身及其全部应用数据，无法恢复。\n\n请输入分身名称以确认：")
            .setView(edit)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (edit.text.toString().trim() == profile.name) viewModel.deleteClone(profile)
                else Toast.makeText(this, "名称不匹配", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun askRenameClone(profile: com.example.clonemanager.data.CloneProfile) {
        val input = android.widget.EditText(this).apply {
            setText(profile.name); setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名分身")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.renameClone(profile, name)
            }
            .show()
    }

    private fun openDetail(profile: com.example.clonemanager.data.CloneProfile) {
        startActivity(
            android.content.Intent(this, com.example.clonemanager.ui.clone.CloneDetailActivity::class.java)
                .putExtra(com.example.clonemanager.ui.clone.CloneDetailActivity.EXTRA_USER_ID, profile.userId)
                .putExtra(com.example.clonemanager.ui.clone.CloneDetailActivity.EXTRA_CLONE_NAME, profile.name)
        )
    }

    private fun showOpResult(op: com.example.clonemanager.repository.OpResult) {
        if (op.success) {
            Toast.makeText(this, "${getString(R.string.op_success)}：${op.message}", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.op_failed)
            .setMessage(buildString {
                append(op.message)
                op.detail?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
            })
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    private fun render(state: HomeUiState) {
        binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE

        val root = state.root
        if (root == null) {
            binding.rootStatusHero.text = "未知"
            binding.rootDetail.visibility = View.GONE
        } else if (root.available) {
            binding.rootStatusHero.text = "已获得"
            binding.rootDetail.visibility = View.VISIBLE
            binding.rootDetail.text = root.output
        } else {
            binding.rootStatusHero.text = "未获得"
            binding.rootDetail.visibility = View.VISIBLE
            binding.rootDetail.text = buildString {
                append("exitCode=").append(root.exitCode).append('\n')
                if (root.output.isNotBlank()) append(root.output).append('\n')
                if (root.stderr.isNotBlank()) append(root.stderr)
            }.trim()
        }

        val limit = state.cloneLimit
        binding.cloneCountHero.text = getString(
            R.string.home_clone_count_value,
            state.clones.size,
            limit?.toString() ?: getString(R.string.home_clone_limit_unknown)
        )

        val error = state.error
        if (error.isNullOrBlank()) binding.errorCard.visibility = View.GONE
        else { binding.errorCard.visibility = View.VISIBLE; binding.errorText.text = error }

        cloneAdapter.operating = state.operating
        cloneAdapter.renameSupported = state.capabilities?.capabilities
            ?.any { it.name == "pm rename-user" && it.detected } == true
        cloneAdapter.submitList(state.clones)
        val empty = state.clones.isEmpty()
        binding.cloneEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.cloneList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showCompatibility() {
        val state = viewModel.uiState.value
        val caps = state.capabilities
        if (caps == null) { viewModel.refresh(); Toast.makeText(this, R.string.cap_detecting, Toast.LENGTH_SHORT).show(); return }

        val b = DialogCompatibilityBinding.inflate(layoutInflater)
        b.capSummary.text = buildString {
            append(getString(R.string.cap_summary_root, if (caps.rootAvailable) "✓" else "✗"))
            append("  ")
            append(getString(R.string.cap_summary_clone, if (caps.cloneTypeSupported) "✓" else "✗"))
            append("  ")
            append(getString(R.string.cap_summary_max, caps.cloneLimit?.toString() ?: "未知"))
            if (state.allUserIds.isNotEmpty()) append("\n").append(getString(R.string.cap_summary_users, state.allUserIds.joinToString(", ")))
        }
        val container = b.capContainer
        addCapRow(container, getString(R.string.cap_root), caps.rootAvailable, caps.rootOutput)
        addCapRow(container, getString(R.string.cap_clone_type), caps.cloneTypeSupported,
            if (caps.cloneTypeSupported) "android.os.usertype.profile.CLONE 已存在" else "未在 dumpsys user 中发现 CLONE")
        caps.capabilities.forEach { addCapabilityRow(container, it) }

        MaterialAlertDialogBuilder(this).setView(b.root).setCancelable(true).show()
            .also { d -> b.btnCapClose.setOnClickListener { d.dismiss() } }
    }

    private fun addCapRow(container: LinearLayout, name: String, ok: Boolean, detail: String?) {
        addRow(container, name,
            if (ok) "✓ 已识别" else "✗ 未识别",
            if (ok) getColor(R.color.status_running_fg) else getColor(R.color.root_fail_fg),
            detail)
    }

    private fun addCapabilityRow(container: LinearLayout, cap: CapabilityInfo) {
        addRow(container, cap.name, cap.displayText,
            if (cap.detected) getColor(R.color.status_running_fg) else getColor(R.color.status_stopped_fg),
            buildString {
                cap.commandLine?.let { append("命令: ").append(it).append('\n') }
                cap.note?.let { append("说明: ").append(it) }
            }.trim().ifBlank { null })
    }

    private fun addRow(container: LinearLayout, name: String, status: String, statusColor: Int, detail: String?) {
        val ctx = container.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(ctx).apply {
            text = name; textSize = 14f
            setTextColor(getColor(android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = status; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(statusColor)
        })
        container.addView(row)
        if (!detail.isNullOrBlank()) container.addView(TextView(ctx).apply {
            text = detail; textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(0, 0, 0, dp(4))
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
