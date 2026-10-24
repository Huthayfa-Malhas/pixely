package app.pixely.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.diagnostics.DiagnosticsReport
import app.pixely.data.diagnostics.Report
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** [report] is null while it's gathered; it's a snapshot, taken again by [refresh]. */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val diagnostics: DiagnosticsReport) : ViewModel() {

    private val _report = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = _report

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _report.value = diagnostics.build() }
    }

    /** A fresh report as a file, for the share sheet. */
    suspend fun export(): File = diagnostics.build().also { _report.value = it }.let { diagnostics.export(it) }
}
