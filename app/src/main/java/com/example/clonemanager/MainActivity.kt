package com.example.clonemanager

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.clonemanager.data.CloneProfile
import com.example.clonemanager.databinding.ActivityMainBinding
import com.example.clonemanager.databinding.DialogCompatibilityBinding
import com.example.clonemanager.repository.CloneRepository
import com.example.clonemanager.repository.OpResult
import com.example.clonemanager.root.RootService
import com.example.clonemanager.system.CommandCapability
import com.example.clonemanager.system.DeviceCapabilities
import com.example.clonemanager.ui.home.CloneListAdapter
import com.example.clonemanager.ui.home.HomeUiState
import com.example.clonemanager.ui.home.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(CloneRepository(RootService()))
    }

    private val cloneAdapter = CloneListAdapter(
        onStart = { clone -> viewModel.startClone(clone) },
        onStop = { clone -> confirmStop(clone) },
        onDelete = { clone -> confirmDelete(clone) },
        onManage = { clone ->
            startActivity(android.content.Intent(this, com.example.clonemanager.ui.clone.CloneDetailActivity::class.java).apply {
                putExtra(com.example.clonemanager.ui.clone.CloneDetailActivity.EXTRA_USER_ID, clone.userId)
                putExtra(com.example.clonemanager.ui.clone.CloneDetailActivity.EXTRA_CLONE_NAME, clone.name)
            })
        },
        onRename = { clone -> showRenameDialog(clone) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cloneList.layoutManager = LinearLayoutManager(this)
        binding.cloneList.adapter = cloneAdapter

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnCompatibility.setOnClickListener { showCompatibility() }
        binding.btnCreate.setOnClickListener { showCreateDialog() }
        binding.btnLogs.setOnClickListener {
            startActivity(android.content.Intent(this, com.example.clonemanager.ui.debug.LogsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.opEvents.collect { op -> showOpResult(op) }
            }
        }
    }

    private fun confirmStop(clone: CloneProfile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.stop_confirm_title)
            .setMessage(getString(R.string.stop_confirm_message, clone.name, clone.userId))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.stop_confirm_action) { _, _ -> viewModel.stopClone(clone) }
            .show()
    }

    private fun confirmDelete(clone: CloneProfile) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), 0)
        }
        val msg = android.widget.TextView(this).apply {
            text = getString(R.string.delete_confirm_message, clone.name, clone.userId)
            textSize = 14f
        }
        val hintLabel = android.widget.TextView(this).apply {
            text = getString(R.string.delete_type_name_hint, clone.name)
            textSize = 13f
            setPadding(0, dp(16), 0, dp(6))
        }
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.delete_type_name_hint, clone.name)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        container.addView(msg); container.addView(hintLabel); container.addView(input)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setView(container)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.delete_confirm_action, null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.isEnabled = false
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    btn.isEnabled = s?.toString()?.trim() == clone.name
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            btn.setOnClickListener { viewModel.deleteClone(clone); dialog.dismiss() }
        }
        dialog.show()
    }

    private fun showCreateDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.create_dialog_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_dialog_title)
            .setView(input)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.create_dialog_button) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createClone(name)
            }
            .show()
    }

    private fun showRenameDialog(clone: CloneProfile) {
        val input = android.widget.EditText(this).apply {
            setText(clone.name); setSelection(clone.name.length)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名「${clone.name}」")
            .setView(input)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton("重命名") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && name != clone.name) viewModel.renameClone(clone, name)
            }
            .show()
    }

    private fun showOpResult(op: OpResult) {
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
            binding.rootDot.setTextColor(resolveColor(com.google.android.material.R.attr.colorOutline))
            binding.rootStatus.setText(R.string.home_root_unknown)
            binding.rootDetail.visibility = View.GONE
        } else if (root.available) {
            binding.rootDot.setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            binding.rootStatus.setText(R.string.home_root_ok)
            binding.rootDetail.visibility = View.VISIBLE
            binding.rootDetail.text = root.output
        } else {
            binding.rootDot.setTextColor(resolveColor(com.google.android.material.R.attr.colorError))
            binding.rootStatus.setText(R.string.home_root_fail)
            binding.rootDetail.visibility = View.VISIBLE
            binding.rootDetail.text = buildString {
                append("exitCode=").append(root.exitCode).append('\n')
                if (root.output.isNotBlank()) append(root.output).append('\n')
                if (root.stderr.isNotBlank()) append(root.stderr)
            }.trim()
        }

        val limit = state.cloneLimit
        binding.cloneCount.text = getString(
            com.example.clonemanager.R.string.home_clone_count_value,
            state.clones.size,
            limit?.toString() ?: getString(com.example.clonemanager.R.string.home_clone_limit_unknown)
        )
        binding.cloneLimitDetail.text = getString(
            com.example.clonemanager.R.string.home_clone_limit_detail,
            if (limit != null) "mMaxAllowedPerParent = $limit" else "未解析到 mMaxAllowedPerParent"
        )

        val error = state.error
        binding.errorCard.visibility = if (error.isNullOrBlank()) View.GONE else View.VISIBLE
        if (!error.isNullOrBlank()) binding.errorText.text = error

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
        if (caps == null) {
            viewModel.refresh()
            Toast.makeText(this, R.string.cap_detecting, Toast.LENGTH_SHORT).show()
            return
        }

        val binding = DialogCompatibilityBinding.inflate(layoutInflater)
        binding.capSummary.text = buildString {
            append(getString(R.string.cap_summary_root, if (caps.rootAvailable) "✓" else "✗"))
            append("  ")
            append(getString(R.string.cap_summary_clone, if (caps.cloneTypeSupported) "✓" else "✗"))
            append("  ")
            append(getString(R.string.cap_summary_max, caps.cloneLimit?.toString() ?: "未知"))
            if (state.allUserIds.isNotEmpty()) {
                append("\n")
                append(getString(R.string.cap_summary_users, state.allUserIds.joinToString(", ")))
            }
        }

        val container = binding.capContainer
        addCapRow(container, getString(R.string.cap_root), caps.rootAvailable, caps.rootOutput)
        addCapRow(container, getString(R.string.cap_clone_type), caps.cloneTypeSupported,
            if (caps.cloneTypeSupported) "android.os.usertype.profile.CLONE 已存在于 dumpsys user" else "未在 dumpsys user 中发现 CLONE 类型")
        caps.capabilities.forEach { addCapabilityRow(container, it) }
        if (caps.helpFailures.isNotEmpty()) {
            addInfoRow(container, getString(R.string.cap_refresh_failed),
                caps.helpFailures.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        }

        MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(true)
            .show()
            .also { dialog -> binding.btnCapClose.setOnClickListener { dialog.dismiss() } }
    }

    private fun addCapRow(container: LinearLayout, name: String, ok: Boolean, detail: String?) {
        addRow(container, name, if (ok) "✓ ${getString(R.string.cap_detected)}" else "✗ ${getString(R.string.cap_not_detected)}",
            if (ok) resolveColor(com.google.android.material.R.attr.colorPrimary) else resolveColor(com.google.android.material.R.attr.colorError), detail)
    }

    private fun addCapabilityRow(container: LinearLayout, cap: CommandCapability) {
        addRow(container, cap.name, cap.displayText,
            if (cap.detected) resolveColor(com.google.android.material.R.attr.colorPrimary) else resolveColor(com.google.android.material.R.attr.colorOutline),
            buildString {
                cap.commandLine?.let { append(getString(R.string.cap_command)).append(": ").append(it).append('\n') }
                cap.note?.let { append(getString(R.string.cap_note)).append(": ").append(it) }
            }.trim().ifBlank { null })
    }

    private fun addInfoRow(container: LinearLayout, name: String, detail: String) {
        addRow(container, name, "!", resolveColor(com.google.android.material.R.attr.colorError), detail)
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
            setTextColor(resolveAttr(android.R.attr.textColorPrimary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = status; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(statusColor)
        })
        container.addView(row)
        if (!detail.isNullOrBlank()) {
            container.addView(TextView(ctx).apply {
                text = detail; textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(resolveAttr(android.R.attr.textColorSecondary))
                setPadding(0, 0, 0, dp(4))
            })
        }
    }

    private fun resolveColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0xFF000000.toInt()); ta.recycle(); return color
    }

    private fun resolveAttr(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0xFF000000.toInt()); ta.recycle(); return color
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
