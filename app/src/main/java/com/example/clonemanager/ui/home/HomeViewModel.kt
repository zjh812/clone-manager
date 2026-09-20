package com.example.clonemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.clonemanager.data.CloneProfile
import com.example.clonemanager.repository.CloneRepository
import com.example.clonemanager.repository.OpResult
import com.example.clonemanager.system.DeviceCapabilities
import com.example.clonemanager.util.RootStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val operating: Boolean = false,
    val root: RootStatus? = null,
    val cloneLimit: Int? = null,
    val clones: List<CloneProfile> = emptyList(),
    val allUserIds: List<Int> = emptyList(),
    val capabilities: DeviceCapabilities? = null,
    val error: String? = null
)

class HomeViewModel(private val repository: CloneRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _opEvents = MutableSharedFlow<OpResult>(extraBufferCapacity = 4)
    val opEvents: SharedFlow<OpResult> = _opEvents.asSharedFlow()

    init { refresh() }

    private var cachedCapabilities: DeviceCapabilities? = null

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                if (cachedCapabilities == null) {
                    val (snap, caps) = repository.loadHomeSnapshotWithCapabilities()
                    cachedCapabilities = caps
                    _uiState.value = HomeUiState(false, root = snap.root, cloneLimit = snap.cloneLimit,
                        clones = snap.clones, allUserIds = snap.allUserIds, capabilities = caps)
                } else {
                    val snap = repository.loadHomeSnapshot()
                    _uiState.value = HomeUiState(false, root = snap.root, cloneLimit = snap.cloneLimit,
                        clones = snap.clones, allUserIds = snap.allUserIds, capabilities = cachedCapabilities)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "未知错误") }
            }
        }
    }

    fun startClone(clone: CloneProfile) = op({ repository.startCloneProfile(clone.userId, clone.name) })
    fun stopClone(clone: CloneProfile) = op({ repository.stopCloneProfile(clone.userId, clone.name) })
    fun createClone(name: String) = op({ repository.createCloneProfile(name) })
    fun deleteClone(clone: CloneProfile) = op({ repository.deleteCloneProfile(clone) })
    fun renameClone(clone: CloneProfile, newName: String) = op({ repository.renameCloneProfile(clone, newName) })

    private fun op(block: suspend () -> OpResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(operating = true) }
            val r = block()
            _opEvents.emit(r)
            _uiState.update { it.copy(operating = false) }
            if (r.success) refresh()
        }
    }

    fun isRenameSupported(): Boolean =
        cachedCapabilities?.capabilities?.any { it.name == "pm rename-user" && it.detected } == true

    class Factory(private val repository: CloneRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) return HomeViewModel(repository) as T
            throw IllegalArgumentException("未知 ViewModel 类型: ${modelClass.name}")
        }
    }
}
