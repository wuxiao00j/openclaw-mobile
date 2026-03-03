package com.openclaw.mobile.ui.config

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.openclaw.mobile.R
import com.openclaw.mobile.databinding.DialogConfigBinding
import com.openclaw.mobile.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ConfigDialogFragment : DialogFragment() {

    private var _binding: DialogConfigBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogConfigBinding.inflate(layoutInflater)
        
        // 预填充已有 API Key
        viewModel.apiKey.value?.let { apiKey ->
            binding.etApiKey.setText(apiKey)
        }

        binding.btnAutoConfig.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                saveApiKey(apiKey)
            } else {
                binding.tilApiKey.error = "请输入 API Key"
            }
        }

        binding.tvViewSupported.setOnClickListener {
            // TODO: 打开支持列表
            Toast.makeText(requireContext(), "支持列表功能开发中", Toast.LENGTH_SHORT).show()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(true)
            .create()
    }

    private fun saveApiKey(apiKey: String) {
        viewModel.configureApiKey(apiKey, requireContext())
        Toast.makeText(requireContext(), "API Key 已保存", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
