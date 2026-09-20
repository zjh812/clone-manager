package com.example.clonemanager.ui.clone

import android.os.Bundle
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

class AddAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAppsBinding
    private lateinit var repo: AppRepository
    private var userId: Int = 0
    private lateinit var adapter: AddableAppAdapter

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
        load()
    }

    private fun load() = lifecycleScope.launch {
        adapter.submit(withContext(Dispatchers.IO) { repo.listAddableApps(userId) })
    }

    private fun installSelected() {
        val selected = adapter.getSelected()
        if (selected.isEmpty()) { Toast.makeText(this, "请先勾选应用", Toast.LENGTH_SHORT).show(); return }
        binding.btnInstallSelected.isEnabled = false
        lifecycleScope.launch {
            var ok = 0; var fail = 0
            val failed = mutableListOf<String>()
            for (app in selected) {
                val r = withContext(Dispatchers.IO) { repo.installExisting(userId, app.packageName) }
                if (r.success) ok++ else { fail++; failed.add("${app.label}: ${r.message}") }
            }
            binding.btnInstallSelected.isEnabled = true
            Toast.makeText(this@AddAppsActivity, buildString {
                append("成功 $ok 个"); if (fail > 0) append("，失败 $fail 个")
            }, Toast.LENGTH_LONG).show()
            if (failed.isNotEmpty()) com.google.android.material.dialog.MaterialAlertDialogBuilder(this@AddAppsActivity)
                .setTitle("部分失败").setMessage(failed.joinToString("\n"))
                .setPositiveButton("确定", null).show()
            if (fail == 0) { setResult(RESULT_OK); finish() } else load()
        }
    }
}

class AddableAppAdapter : RecyclerView.Adapter<AddableAppAdapter.VH>() {

    private val items = mutableListOf<AppInfo>()
    private val checked = mutableSetOf<String>()

    fun submit(list: List<AppInfo>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
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
        try { holder.b.appIcon.setImageDrawable(
            holder.itemView.context.packageManager.getApplicationIcon(app.packageName))
        } catch (_: Exception) { holder.b.appIcon.setImageResource(android.R.drawable.sym_def_app_icon) }
        holder.b.chkSelect.setOnCheckedChangeListener(null)
        holder.b.chkSelect.isChecked = app.packageName in checked
        holder.b.chkSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checked.add(app.packageName) else checked.remove(app.packageName)
        }
        holder.itemView.setOnClickListener { holder.b.chkSelect.toggle() }
    }

    override fun getItemCount(): Int = items.size
}
