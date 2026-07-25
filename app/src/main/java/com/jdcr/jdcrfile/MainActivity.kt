package com.jdcr.jdcrfile

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.jdcr.jdcrdatabase.logcache.JdcrDBFeatLog
import com.jdcr.jdcrfile.permission.JdcrFilePermissionUtils
import com.jdcr.jdcrfile.ui.theme.JdcrFileTheme
import com.jdcr.jdcrlog.JdcrLog
import com.jdcr.jdcrlog.JdcrLogBase
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private var hasStoragePermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        JdcrLogBase.dbServer = JdcrDBFeatLog
        JdcrLog.enable(true)
        JdcrLog.i("测试1")
        JdcrLog.i("测试2")
        lifecycleScope.launch {
            delay(1700)
            JdcrDBFeatLog.read(
                System.currentTimeMillis() - 5000,
                System.currentTimeMillis() + 5000
            ).onSuccess { logs ->
                logs.forEach { Log.d("jdcr_", it.message) }
            }
        }

        hasStoragePermission = JdcrFilePermissionUtils.hasMaxStoragePermission(this)
        setContent {
            JdcrFileTheme {
                FilePickerScreen(
                    activity = this,
                    hasPermission = hasStoragePermission,
                    onPermissionChanged = { hasStoragePermission = it }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasStoragePermission = JdcrFilePermissionUtils.hasMaxStoragePermission(this)
    }
}

private sealed class DatabaseSyncState {
    object Idle : DatabaseSyncState()

    data class Success(val summary: FileDatabaseSync.Summary) : DatabaseSyncState()

    data class Failure(val message: String) : DatabaseSyncState()
}

private data class DirectoryColumn(
    val directory: File,
    val entries: List<File> = emptyList(),
    val errorMessage: String? = null,
    val databaseSyncState: DatabaseSyncState = DatabaseSyncState.Idle
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePickerScreen(
    activity: FragmentActivity,
    hasPermission: Boolean,
    onPermissionChanged: (Boolean) -> Unit
) {
    val rootDirectory = remember { File(JdcrFileUtils.getExternalStorageDir()) }
    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var columns by remember { mutableStateOf<List<DirectoryColumn>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var navigationJob by remember { mutableStateOf<Job?>(null) }

    suspend fun readDirectory(directory: File): DirectoryColumn = withContext(Dispatchers.IO) {
        runCatching {
            directory.listFiles()
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                ?: if (directory.canRead()) emptyList() else error("没有权限读取该目录")
        }.fold(
            onSuccess = { DirectoryColumn(directory = directory, entries = it) },
            onFailure = {
                DirectoryColumn(
                    directory = directory,
                    errorMessage = it.message ?: "读取目录失败"
                )
            }
        )
    }

    suspend fun loadDirectory(directory: File): DirectoryColumn {
        val column = readDirectory(directory)
        if (column.errorMessage != null) {
            return column
        }

        val syncState = FileDatabaseSync.sync(column.entries).fold(
            onSuccess = { DatabaseSyncState.Success(it) },
            onFailure = {
                DatabaseSyncState.Failure(it.message ?: "未知数据库错误")
            }
        )
        return column.copy(databaseSyncState = syncState)
    }

    fun loadRoot() {
        navigationJob?.cancel()
        navigationJob = scope.launch {
            isLoading = true
            val rootColumn = loadDirectory(rootDirectory)
            coroutineContext.ensureActive()
            columns = listOf(rootColumn)
            selectedFile = null
            isLoading = false
            horizontalScroll.scrollTo(0)
        }
    }

    fun openDirectory(columnIndex: Int, directory: File) {
        navigationJob?.cancel()
        navigationJob = scope.launch {
            isLoading = true
            val nextColumn = loadDirectory(directory)
            coroutineContext.ensureActive()
            columns = columns.take(columnIndex + 1) + nextColumn
            selectedFile = null
            isLoading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadRoot()
        } else {
            navigationJob?.cancel()
            navigationJob = null
            columns = emptyList()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("JdcrFile", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = columns.lastOrNull()?.directory?.absolutePath
                                ?: rootDirectory.absolutePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadRoot() },
                        enabled = hasPermission && !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新目录")
                    }
                }
            )
        },
        bottomBar = {
            SelectionBar(
                selectedFile = selectedFile,
                currentDirectory = columns.lastOrNull()?.directory,
                databaseSyncState = columns.lastOrNull()?.databaseSyncState
                    ?: DatabaseSyncState.Idle
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (!hasPermission) {
                PermissionContent(
                    onRequestPermission = {
                        JdcrFilePermissionUtils.requestMaxStoragePermission(activity) {
                            onPermissionChanged(
                                JdcrFilePermissionUtils.hasMaxStoragePermission(activity)
                            )
                        }
                    }
                )
            } else {
                DirectoryBrowser(
                    columns = columns,
                    selectedFile = selectedFile,
                    horizontalScroll = horizontalScroll,
                    onDirectoryClick = ::openDirectory,
                    onFileClick = { columnIndex, file ->
                        columns = columns.take(columnIndex + 1)
                        selectedFile = file
                    }
                )
            }

            if (isLoading) {
                Text(
                    text = "正在读取…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("访问本机文件", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "需要“所有文件访问权限”才能从存储根目录逐级浏览。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestPermission) {
            Text("授予文件访问权限")
        }
    }
}

@Composable
private fun DirectoryBrowser(
    columns: List<DirectoryColumn>,
    selectedFile: File?,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    onDirectoryClick: (Int, File) -> Unit,
    onFileClick: (Int, File) -> Unit
) {
    LaunchedEffect(horizontalScroll.maxValue) {
        if (columns.size > 1) {
            horizontalScroll.animateScrollTo(horizontalScroll.maxValue)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScroll)
    ) {
        columns.forEachIndexed { index, column ->
            val selectedDirectory = columns.getOrNull(index + 1)?.directory
            DirectoryPane(
                column = column,
                selectedDirectory = selectedDirectory,
                selectedFile = selectedFile,
                onDirectoryClick = { onDirectoryClick(index, it) },
                onFileClick = { onFileClick(index, it) }
            )
            if (index != columns.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun DirectoryPane(
    column: DirectoryColumn,
    selectedDirectory: File?,
    selectedFile: File?,
    onDirectoryClick: (File) -> Unit,
    onFileClick: (File) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .width(280.dp)
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        when {
            column.errorMessage != null -> item {
                DirectoryMessage(
                    text = column.errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            column.entries.isEmpty() -> item {
                DirectoryMessage(
                    text = "此文件夹为空",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> items(column.entries, key = { it.absolutePath }) { file ->
                val isSelected = file == selectedDirectory || file == selectedFile
                FileRow(
                    file = file,
                    selected = isSelected,
                    onClick = {
                        if (file.isDirectory) onDirectoryClick(file) else onFileClick(file)
                    }
                )
            }
        }
    }
}

@Composable
private fun FileRow(file: File, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.name.ifEmpty { file.absolutePath },
            style = MaterialTheme.typography.bodyMedium,
            color = if (file.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (file.isDirectory) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "打开 ${file.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DirectoryMessage(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(20.dp)
    )
}

@Composable
private fun SelectionBar(
    selectedFile: File?,
    currentDirectory: File?,
    databaseSyncState: DatabaseSyncState
) {
    val databaseText = when (databaseSyncState) {
        DatabaseSyncState.Idle -> "数据库 尚未同步"
        is DatabaseSyncState.Success -> {
            val summary = databaseSyncState.summary
            if (summary.scannedFileCount == 0) {
                "数据库 当前目录没有普通文件"
            } else {
                "数据库 已同步 ${summary.scannedFileCount} 个：" +
                    "新增 ${summary.insertedCount}，更新 ${summary.updatedCount}"
            }
        }

        is DatabaseSyncState.Failure -> "数据库同步失败：${databaseSyncState.message}"
    }
    val databaseColor = if (databaseSyncState is DatabaseSyncState.Failure) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedFile != null) "已选择" else "当前位置",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = selectedFile?.absolutePath
                        ?: currentDirectory?.absolutePath
                        ?: "等待授权",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = databaseText,
                style = MaterialTheme.typography.bodySmall,
                color = databaseColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
