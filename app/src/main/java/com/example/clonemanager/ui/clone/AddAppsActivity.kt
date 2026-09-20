package com.example.clonemanager.ui.clone

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clonemanager.databinding.ActivityAddAppsBinding
import com.example.clonemanager.databinding.ItemAddableAppBinding
import com.example.clonemanager.data.AppInfo
import com.example.clonemanager.repository.AppRepository
import com.example.clonemanager.root.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 添加应用到分身：列出主系统已安装、但分身内尚未安装的应用，多选后批量 install-existing。
 *
 * 支持：
 * - 搜索框（按 label / packageName 实时过滤）
 * - 「屏蔽系统应用」开关（默认开启，隐藏 FLAG_SYSTEM 应用）
 */
class AddAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAppsBinding
    private lateinit var repo: AppRepository
    private var userId: Int = 0
    private lateinit var adapter: AddableAppAdapter

    /** 全量可添加列表（未过滤） */
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra(CloneDetailActivity.EXTRA_USER_ID, -1)
        if (userId <= 0) { finish(); return }
        val cloneName = intent.getStringExtra(CloneDetailActivity.EXTRA_CLONE_NAME) ?: "分身"

        repo = AppRepository(this, RootService())
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "添加应用到「$cloneName」"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = AddableAppAdapter()
        binding.addList.layoutManager = LinearLayoutManager(this)
        binding.addList.adapter = adapter

        binding.btnInstallSelected.setOnClickListener { installSelected() }

        binding.chkHideSystem.isChecked = true
        binding.chkHideSystem.setOnCheckedChangeListener { _, _ -> applyFilter() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { repo.listAddableApps(userId) }
            allApps = apps
            applyFilter()
        }
    }

    private fun applyFilter() {
        val query = binding.searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val hideSystem = binding.chkHideSystem.isChecked

        val filtered = allApps.filter { app ->
            if (hideSystem && app.isSystemApp) return@filter false
            if (query.isEmpty()) return@filter true
            app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
        }
        adapter.submit(filtered)
        binding.countText.text = "${filtered.size} 个应用"
    }

    private fun installSelected() {
        val selected = adapter.getSelected()
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先勾选应用", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnInstallSelected.isEnabled = false
        lifecycleScope.launch {
            var ok = 0; var fail = 0
            val failed = mutableListOf<String>()
            for (app in selected) {
                val r = withContext(Dispatchers.IO) { repo.installExisting(userId, app.packageName) }
                if (r.success) ok++ else { fail++; failed.add("${app.label}: ${r.message}") }
            }
            binding.btnInstallSelected.isEnabled = true
            val msg = buildString {
                append("成功 $ok 个")
                if (fail > 0) append("，失败 $fail 个")
            }
            Toast.makeText(this@AddAppsActivity, msg, Toast.LENGTH_LONG).show()
            if (failed.isNotEmpty()) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@AddAppsActivity)
                    .setTitle("部分失败")
                    .setMessage(failed.joinToString("\n"))
                    .setPositiveButton("确定", null)
                    .show()
            }
            if (fail == 0) {
                setResult(RESULT_OK)
                finish()
            } else {
                load()
            }
        }
    }
}

class AddableAppAdapter : RecyclerView.Adapter<AddableAppAdapter.VH>() {

    private val items = mutableListOf<AppInfo>()
    private val checked = mutableSetOf<String>()

    fun submit(list: List<AppInfo>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun getSelected(): List<AppInfo> = items.filter { it.packageName in checked }

    class VH(val b: ItemAddableAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAddableAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.b.appLabel.text = app.label
        holder.b.appPackage.text = app.packageName
        try {
            holder.b.appIcon.setImageDrawable(
                holder.itemView.context.packageManager.getApplicationIcon(app.packageName)
            )
        } catch (_: Exception) {
            holder.b.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        holder.b.chkSelect.setOnCheckedChangeListener(null)
        holder.b.chkSelect.isChecked = app.packageName in checked
        holder.b.chkSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checked.add(app.packageName) else checked.remove(app.packageName)
        }
        holder.itemView.setOnClickListener {
            holder.b.chkSelect.toggle()
        }
    }

    override fun getItemCount(): Int = items.size
}
