package com.example.dailyschedule

import android.Manifest
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")
private val DARK_KEY = booleanPreferencesKey("dark_mode")
private val THEME_KEY = stringPreferencesKey("theme")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 700)
        }
        setContent { DailyScheduleApp() }
    }
}

private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun timeText(minute: Int): String = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScheduleApp() {
    val context = LocalContext.current
    val dao = remember { ScheduleDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val tasks by dao.observeTasks().collectAsState(initial = emptyList())
    val completions by dao.observeCompletions(todayKey()).collectAsState(initial = emptyList())
    var dark by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var startMinute by remember { mutableIntStateOf(8 * 60) }
    var duration by remember { mutableIntStateOf(60) }
    var reminder by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        dark = context.dataStore.data.first()[DARK_KEY] ?: false
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val taskList = dao.allTasks()
                val doneList = dao.allCompletions()
                val root = JSONObject()
                val ta = JSONArray()
                taskList.forEach { t ->
                    ta.put(JSONObject().put("id", t.id).put("title", t.title)
                        .put("startMinute", t.startMinute).put("durationMinutes", t.durationMinutes)
                        .put("reminderEnabled", t.reminderEnabled).put("enabled", t.enabled))
                }
                val ca = JSONArray()
                doneList.forEach { c ->
                    ca.put(JSONObject().put("taskId", c.taskId).put("date", c.date)
                        .put("completed", c.completed).put("actualMinutes", c.actualMinutes))
                }
                root.put("formatVersion", 1).put("tasks", ta).put("completions", ca)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString(2)) }
                Toast.makeText(context, "ব্যাকআপ সংরক্ষণ হয়েছে", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "ব্যাকআপ ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("ফাইল পড়া যায়নি")
                val root = JSONObject(text)
                require(root.optInt("formatVersion") == 1) { "অসমর্থিত ব্যাকআপ ফরম্যাট" }
                val taskArray = root.getJSONArray("tasks")
                val completionArray = root.getJSONArray("completions")
                val importedTasks = (0 until taskArray.length()).map { i ->
                    val o = taskArray.getJSONObject(i)
                    ScheduleTask(
                        id = o.optLong("id"), title = o.getString("title"),
                        startMinute = o.getInt("startMinute"), durationMinutes = o.getInt("durationMinutes"),
                        reminderEnabled = o.optBoolean("reminderEnabled", true), enabled = o.optBoolean("enabled", true)
                    )
                }
                val importedCompletions = (0 until completionArray.length()).map { i ->
                    val o = completionArray.getJSONObject(i)
                    TaskCompletion(o.getLong("taskId"), o.getString("date"), o.getBoolean("completed"), o.optInt("actualMinutes", 0))
                }
                dao.replaceAll(importedTasks, importedCompletions)
                dao.allTasks().forEach { ReminderScheduler.schedule(context, it) }
                Toast.makeText(context, "ব্যাকআপ রিস্টোর হয়েছে", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "রিস্টোর ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("Daily Schedule", fontWeight = FontWeight.Bold)
                        Text(todayKey(), style = MaterialTheme.typography.labelSmall)
                    }
                })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("আজ") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Assessment, null) }, label = { Text("রিপোর্ট") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("সেটিংস") })
                }
            },
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "কাজ যোগ করুন")
                }
            }
        ) { padding ->
            when (tab) {
                0 -> TodayScreen(
                    Modifier.padding(padding), tasks, completions,
                    onToggle = { task, checked ->
                        scope.launch {
                            dao.putCompletion(TaskCompletion(task.id, todayKey(), checked, if (checked) task.durationMinutes else 0))
                        }
                    },
                    onDelete = { task ->
                        scope.launch { dao.deleteTask(task); ReminderScheduler.cancel(context, task) }
                    }
                )
                1 -> ReportsScreen(Modifier.padding(padding), tasks.size, completions)
                else -> SettingsScreen(
                    Modifier.padding(padding), dark,
                    onDarkChange = { value ->
                        dark = value
                        scope.launch { context.dataStore.edit { it[DARK_KEY] = value; it[THEME_KEY] = if (value) "DARK" else "LIGHT" } }
                    },
                    onExport = { exportLauncher.launch("daily-schedule-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("নতুন কাজ যোগ করুন") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("কাজের নাম") }, singleLine = true)
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> startMinute = h * 60 + m },
                            startMinute / 60, startMinute % 60, true).show()
                    }) { Text("শুরুর সময়: ${timeText(startMinute)}") }
                    OutlinedTextField(
                        value = duration.toString(), onValueChange = { duration = it.toIntOrNull()?.coerceIn(5, 720) ?: duration },
                        label = { Text("সময়কাল (মিনিট)") }, singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                        Text("রিমাইন্ডার চালু")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) scope.launch {
                        val task = ScheduleTask(title = title.trim(), startMinute = startMinute, durationMinutes = duration, reminderEnabled = reminder)
                        val id = dao.insertTask(task)
                        ReminderScheduler.schedule(context, task.copy(id = id))
                        title = ""
                        showAdd = false
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("বাতিল") } }
        )
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier,
    tasks: List<ScheduleTask>,
    completions: List<TaskCompletion>,
    onToggle: (ScheduleTask, Boolean) -> Unit,
    onDelete: (ScheduleTask) -> Unit
) {
    val completedIds = completions.filter { it.completed }.map { it.taskId }.toSet()
    val done = completedIds.size
    val progress = if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("আজকের অগ্রগতি", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("$done / ${tasks.size} কাজ সম্পন্ন • ${(progress * 100).toInt()}%")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("এখনও কোনো কাজ যোগ করা হয়নি। + চাপ দিয়ে শুরু করো।")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.id in completedIds, onCheckedChange = { onToggle(task, it) })
                            Column(Modifier.weight(1f)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold)
                                Text("${timeText(task.startMinute)} • ${task.durationMinutes} মিনিট", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onDelete(task) }) {
                                Icon(Icons.Default.Delete, contentDescription = "মুছে ফেলুন")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsScreen(modifier: Modifier, taskCount: Int, completions: List<TaskCompletion>) {
    val done = completions.count { it.completed }
    val actual = completions.filter { it.completed }.sumOf { it.actualMinutes }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("আজকের রিপোর্ট", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ReportCard("মোট নির্ধারিত কাজ", "$taskCount")
        ReportCard("সম্পন্ন কাজ", "$done")
        ReportCard("সম্পন্ন কাজের হার", if (taskCount == 0) "০%" else "${(done * 100 / taskCount)}%")
        ReportCard("লগ করা সময়", "$actual মিনিট")
        Text("নোট: এই প্রাথমিক সংস্করণে রিপোর্ট আজকের জন্য। পরবর্তী সংস্করণে সাপ্তাহিক ও মাসিক রিপোর্ট যোগ করা যাবে।", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReportCard(label: String, value: String) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("সেটিংস", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ডার্ক মোড")
                    Text("অ্যাপের রং পরিবর্তন", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = dark, onCheckedChange = onDarkChange)
            }
        }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileDownload, null)
            Spacer(Modifier.width(8.dp))
            Text("JSON ব্যাকআপ তৈরি")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, null)
            Spacer(Modifier.width(8.dp))
            Text("ব্যাকআপ রিস্টোর")
        }
        Text("সব রুটিন ও রিপোর্ট এই ফোনের স্থানীয় ডেটাবেসে রাখা হয়। ব্যাকআপ ফাইল নিরাপদ স্থানে রাখবে।", style = MaterialTheme.typography.bodySmall)
    }
}
