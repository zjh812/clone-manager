package com.example.clonemanager.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clonemanager.databinding.ActivityLogsBinding
import com.example.clonemanager.databinding.ItemLogBinding
import com.example.clonemanager.util.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        adapter = LogAdapter()
        binding.logList.layoutManager = LinearLayoutManager(this)
        binding.logList.adapter = adapter
        binding.btnCopy.setOnClickListener { copyAll() }
        binding.btnClear.setOnClickListener { confirmClear() }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val entries = Logger.entries().reversed()
        adapter.submit(entries.map { it.render() })
        binding.logCount.text = "共 ${entries.size} 条（最多保留 ${Logger.MAX_ENTRIES} 条，超出自动丢弃最旧）"
    }

    private fun copyAll() {
        val text = Logger.dump()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CloneManager logs", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空日志？")
            .setMessage("将删除全部 root 命令执行记录，不可恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ -> Logger.clear(); refresh() }
            .show()
    }
}

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {
    private val items = mutableListOf<String>()
    fun submit(list: List<String>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    class VH(val b: ItemLogBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }
    override fun onBindViewHolder(holder: VH, position: Int) { holder.b.logText.text = items[position] }
    override fun getItemCount(): Int = items.size
}
