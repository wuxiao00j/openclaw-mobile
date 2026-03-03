package com.openclaw.mobile.ui.logs

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.openclaw.mobile.R
import com.openclaw.mobile.databinding.ActivityLogsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val viewModel: LogsViewModel by viewModels()
    private lateinit var adapter: LogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.view_logs)
        }
    }

    private fun setupRecyclerView() {
        adapter = LogsAdapter()
        binding.recyclerViewLogs.apply {
            layoutManager = LinearLayoutManager(this@LogsActivity)
            adapter = this@LogsActivity.adapter
        }

        // 自动滚动到底部
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (viewModel.autoScroll.value) {
                    binding.recyclerViewLogs.scrollToPosition(adapter.itemCount - 1)
                }
            }
        })

        binding.switchAutoScroll.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoScroll(isChecked)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.logs.collect { logs ->
                        adapter.submitList(logs)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear -> {
                viewModel.clearLogs()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_export -> {
                exportLogs()
                true
            }
            R.id.action_filter_debug -> {
                item.isChecked = !item.isChecked
                viewModel.setFilterDebug(item.isChecked)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportLogs() {
        val file = viewModel.exportLogs()
        if (file != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this@LogsActivity,
                    "${packageName}.fileprovider",
                    file
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享日志"))
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
}
