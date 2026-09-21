package com.example.clonemanager.ui.clone

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clonemanager.R
import com.example.clonemanager.data.AppInfo
import com.example.clonemanager.databinding.ActivityCloneDetailBinding
import com.example.clonemanager.databinding.ItemInstalledAppBinding
import com.example.clonemanager.repository.AppRepository
import com.example.clonemanager.repository.BackupRepository
import com.example.clonemanager.repository.OpResult
import com.example.clonemanager.root.RootService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分身详情页：应用管理 + 备份/恢复。
 */
class CloneDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_CLONE_NAME = "extra_clone_name"
        const val REQ_ADD_APPS = 1001
    }

    private lateinit var binding: ActivityCloneDetailBinding
    private lateinit var repo: AppRepository
    private lateinit var backupRepo: BackupRepository
    private var userId: Int = 0
    private lateinit var cloneName: String
    private lateinit var adapter: InstalledAppAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var filterTab: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloneDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        cloneName = intent.getStringExtra(EXTRA_CLONE_NAME) ?: "分身"
        if (userId <= 0) { finish(); return }

        repo = AppRepository(this, RootService())
        backupRepo = BackupRepository(this, RootService())

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = cloneName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cloneInfo.text = "User ID: $userId\n类型: android.os.usertype.profile.CLONE"

        adapter = InstalledAppAdapter(
            onLaunch = { app -> launchApp(app) },
            onClear = { app -> confirmClear(app) },
            onUninstall = { app -> confirmUninstall(app) }
        )
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        binding.btnAddApp.setOnClickListener {
            startActivityForResult(
                Intent(this, AddAppsActivity::class.java).apply {
                    putExtra(EXTRA_USER_ID, userId)
                    putExtra(EXTRA_CLONE_NAME, cloneName)
                },
                REQ_ADD_APPS
            )
        }

        binding.btnBackup.setOnClickListener { confirmBackup() }
        binding.btnRestore.setOnClickListener { showRestoreDialog() }

        binding.filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filterTab = tab.position
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (userId > 0 && ::adapter.isInitialized) loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            binding.appCount.text = "加载中…"
            val apps = withContext(Dispatchers.IO) { repo.listInstalledApps(userId) }
            allApps = apps
            applyFilter()
        }
    }

    private fun applyFilter() {
        val query = binding.searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = allApps.filter { app ->
            when (filterTab) {
                1 -> if (app.isSystemApp) return@filter false
                2 -> if (!app.isSystemApp) return@filter false
            }
            if (query.isEmpty()) return@filter true
            app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
        }
        adapter.submit(filtered)
        binding.appCount.text = "显示 ${filtered.size} / ${allApps.size} 个应用"
    }

    private fun launchApp(app: AppInfo) {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { repo.launchPackage(userId, app.packageName) }
            showResult(r)
        }
    }

    private fun confirmClear(app: AppInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("清除数据？")
            .setMessage("应用：${app.label}\n分身：$cloneName\nUser ID：$userId\n\n此操作会删除该分身中此应用的所有数据，不可撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清除") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { repo.clearData(userId, app.packageName) }
                    showResult(r)
                }
            }
            .show()
    }

    private fun confirmUninstall(app: AppInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("卸载应用？")
            .setMessage("应用：${app.label}（${app.packageName}）\n分身：$cloneName\nUser ID：$userId\n\n仅从该分身卸载，不影响主用户。")
            .setNegativeButton("取消", null)
            .setPositiveButton("卸载") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { repo.uninstallPackage(userId, app.packageName) }
                    showResult(r)
                    if (r.success) loadApps()
                }
            }
            .show()
    }

    private fun confirmBackup() {
        MaterialAlertDialogBuilder(this)
            .setTitle("备份「$cloneName」？")
            .setMessage(
                "将把分身 User $userId 下所有应用的内部数据打包为 tar.gz。\n\n" +
                    "保存位置：\n" +
                    "/sdcard/Android/data/${packageName}/files/backups/\n\n" +
                    "备份过程中请勿操作分身，数据量较大时可能需要数分钟。"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("开始备份") { _, _ ->
                runWithProgress("正在备份…") { backupRepo.backupClone(userId, cloneName) }
            }
            .show()
    }

    private fun showRestoreDialog() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { backupRepo.listBackups(userId) }
            if (list.isEmpty()) {
                MaterialAlertDialogBuilder(this@CloneDetailActivity)
                    .setTitle("没有备份")
                    .setMessage("分身 $cloneName 还没有任何备份文件。")
                    .setPositiveButton("确定", null)
                    .show()
                return@launch
            }
            val items = list.map {
                "${backupRepo.formatSize(it.sizeBytes)}  ·  " +
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(it.timestamp))
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@CloneDetailActivity)
                .setTitle("选择要恢复的备份")
                .setItems(items) { _, which -> confirmRestore(list[which]) }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun confirmRestore(b: BackupRepository.BackupInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("恢复备份？")
            .setMessage(
                "将用以下备份覆盖分身 User $userId 的全部应用数据：\n\n" +
                    "文件：${b.fileName}\n" +
                    "大小：${backupRepo.formatSize(b.sizeBytes)}\n\n" +
                    "⚠ 此操作会：\n" +
                    "1. 停止当前分身\n" +
                    "2. 覆盖所有应用数据\n" +
                    "3. 用备份时的数据替换现有数据\n\n" +
                    "当前数据将丢失，无法撤销。"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("确认恢复") { _, _ ->
                runWithProgress("正在恢复…") { backupRepo.restoreBackup(userId, b) }
            }
            .show()
    }

    private fun runWithProgress(task: String, block: suspend () -> OpResult) {
        val progress = android.app.ProgressDialog(this).apply {
            setTitle("请稍候")
            setMessage(task)
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { block() }
            progress.dismiss()
            showResult(r)
        }
    }

    private fun showResult(r: OpResult) {
        if (r.success) {
            Toast.makeText(this, r.message, Toast.LENGTH_SHORT).show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.op_failed)
                .setMessage(if (r.detail != null) "${r.message}\n\n${r.detail}" else r.message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADD_APPS && resultCode == RESULT_OK) loadApps()
    }
}

class InstalledAppAdapter(
    private val onLaunch: (AppInfo) -> Unit,
    private val onClear: (AppInfo) -> Unit,
    private val onUninstall: (AppInfo) -> Unit
) : RecyclerView.Adapter<InstalledAppAdapter.VH>() {

    private val items = mutableListOf<AppInfo>()

    fun submit(list: List<AppInfo>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(val b: ItemInstalledAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemInstalledAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
        holder.b.btnLaunch.setOnClickListener { onLaunch(app) }
        holder.b.btnClear.setOnClickListener { onClear(app) }
        holder.b.btnUninstall.setOnClickListener { onUninstall(app) }
    }

    override fun getItemCount(): Int = items.size
}
