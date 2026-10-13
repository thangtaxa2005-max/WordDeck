package vn.worddeck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    set: StudySet,
    existing: List<Card>,
    a: MainActivity,
    close: () -> Unit,
    save: (StudySet, List<Card>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<Card>>(emptyList()) }
    var preview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Card?>(null) }
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    text =
                        a.contentResolver.openInputStream(uri)!!.bufferedReader().use {
                            it.readText()
                        }
                    csv =
                        a.contentResolver.getType(uri)?.contains("csv") == true ||
                            uri.toString().lowercase().contains("csv")
                    preview = false
                } catch (e: Exception) {
                    error = "Không đọc được file: ${e.message}"
                }
            }
        }
    val known = existing.filter { it.setId == set.id }.map { it.word.trim().lowercase() }.toSet()
    val duplicates =
        rows.groupingBy { it.word.trim().lowercase() }.eachCount().filterValues { it > 1 }.keys +
            known
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(close, enabled = !busy) { Text("← Quay lại") }
            Title("Nhập từ vựng", set.name)
        }
        item {
            Text(
                "Mỗi dòng: từ - nghĩa, hoặc chỉ từ. CSV: word,meaning,phonetic,kind,example. Bạn có thể chỉnh từng hàng trước khi lưu."
            )
            TextButton(
                { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                enabled = !busy,
            ) {
                Text("Chọn file CSV / TXT")
            }
            Row {
                Checkbox(csv, { csv = it })
                Text("Đọc dạng CSV")
            }
        }
        if (!preview)
            item {
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text("Dán danh sách từ") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Action("Xem trước", text.isNotBlank()) {
                    try {
                        rows = Importer.parse(text, set.id)
                        preview = true
                        error = ""
                    } catch (e: Exception) {
                        error = e.message ?: "Sai định dạng"
                    }
                }
            }
        if (error.isNotEmpty()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (preview) {
            item {
                Panel {
                    Text(
                        "${rows.size} thẻ · ${rows.count{it.word.trim().lowercase() in duplicates}} hàng trùng · ${rows.count{it.meaning.isBlank()}} thiếu nghĩa"
                    )
                    Text(status)
                    Action("Tự điền phiên âm, loại từ, nghĩa, ví dụ", !busy && rows.isNotEmpty()) {
                        busy = true
                        scope.launch {
                            try {
                                val dictionary = Dictionary()
                                rows.toList().forEachIndexed { i, c ->
                                    status = "Đang tra ${i+1}/${rows.size}: ${c.word}"
                                    val updated = dictionary.enrich(c)
                                    rows = rows.map { if (it.id == c.id) updated else it }
                                }
                                status =
                                    "Đã tra xong. Hãy kiểm tra bản dịch và nhập tay phần còn thiếu."
                            } finally {
                                busy = false
                            }
                        }
                    }
                    Text("Dịch tự động có thể sai ngữ cảnh. Lần đầu cần tải mô hình dịch.")
                    TextButton(
                        {
                            rows =
                                rows
                                    .filter { it.word.trim().lowercase() !in known }
                                    .distinctBy { it.word.trim().lowercase() }
                        },
                        enabled = !busy,
                    ) {
                        Text("Loại tất cả từ trùng")
                    }
                    TextButton(
                        {
                            text =
                                rows.joinToString("\n") { c ->
                                    listOf(c.word, c.meaning, c.phonetic, c.kind, c.example)
                                        .joinToString(",") {
                                            "\"" + it.replace("\"", "\"\"") + "\""
                                        }
                                }
                            csv = true
                            preview = false
                        },
                        enabled = !busy,
                    ) {
                        Text("Sửa hàng loạt danh sách")
                    }
                }
            }
            itemsIndexed(rows, key = { _, c -> c.id }) { _, c ->
                Panel {
                    Text(c.word + if (c.word.trim().lowercase() in duplicates) " · Trùng" else "")
                    Text(c.meaning.ifBlank { "Chưa có nghĩa" })
                    Text("${c.phonetic} ${c.kind}")
                    Row {
                        TextButton({ edit = c }, enabled = !busy) { Text("Sửa") }
                        TextButton({ rows = rows.filter { it.id != c.id } }, enabled = !busy) {
                            Text("Bỏ hàng")
                        }
                    }
                }
            }
            item {
                Action(
                    "Lưu ${rows.size} thẻ",
                    !busy &&
                        rows.isNotEmpty() &&
                        rows.all { it.word.isNotBlank() && it.meaning.isNotBlank() } &&
                        rows.none { it.word.trim().lowercase() in duplicates },
                ) {
                    save(set, rows)
                }
                if (rows.any { it.meaning.isBlank() }) Text("Điền đủ nghĩa để lưu.")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    edit?.let { c ->
        CardDialog(c, { edit = null }) { updated ->
            rows = rows.map { if (it.id == c.id) updated else it }
            edit = null
        }
    }
}
