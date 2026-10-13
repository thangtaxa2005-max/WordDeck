package vn.worddeck

import android.Manifest
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import kotlinx.coroutines.launch

val Blue = Color(0xFF485AFF)
val Green = Color(0xFF19AA82)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this) { ready = it == TextToSpeech.SUCCESS }
        setContent { WordDeck(viewModel(), this) }
    }

    fun speak(word: String) {
        val locale =
            if (getSharedPreferences("settings", 0).getString("voice", "US") == "UK") Locale.UK
            else Locale.US
        if (!ready || (tts?.setLanguage(locale) ?: -1) < 0) {
            Toast.makeText(
                    this,
                    "Chưa có giọng tiếng Anh. Hãy cài giọng đọc trong cài đặt Android.",
                    Toast.LENGTH_LONG,
                )
                .show()
            return
        }
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun Action(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}

@Composable
fun Field(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun Title(title: String, subtitle: String = "") {
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    if (subtitle.isNotEmpty())
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

@Composable
fun WordDeck(vm: DeckModel, activity: MainActivity) {
    val folders by vm.folders.collectAsState()
    val sets by vm.sets.collectAsState()
    val cards by vm.cards.collectAsState()
    val progress by vm.progress.collectAsState()
    val logs by vm.logs.collectAsState()
    val prefs = remember { activity.getSharedPreferences("settings", 0) }
    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark", false)) }
    var page by rememberSaveable { mutableStateOf("Trang chủ") }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var setId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderEdit by remember { mutableStateOf<Folder?>(null) }
    var setEdit by remember { mutableStateOf<StudySet?>(null) }
    var cardEdit by remember { mutableStateOf<Card?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var stars by rememberSaveable { mutableStateOf(false) }
    var session by remember { mutableStateOf<List<Card>>(emptyList()) }
    var sessionMode by remember { mutableStateOf("Học") }
    var importSet by remember { mutableStateOf<StudySet?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(page, folderId, setId) { listState.scrollToItem(0) }
    val message by vm.message.collectAsState()
    LaunchedEffect(page) {
        if (page == "Buổi học" && session.isEmpty()) page = "Trang chủ"
        if (page == "Nhập" && importSet == null) page = "Thư viện"
    }
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            vm.message.value = ""
        }
    }
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFABB3FF),
                background = Color(0xFF151827),
                surface = Color(0xFF202438),
            )
        else
            lightColorScheme(
                primary = Blue,
                background = Color(0xFFF5F6FD),
                surface = Color.White,
                secondary = Green,
                primaryContainer = Color(0xFFE9ECFF),
                secondaryContainer = Color(0xFFE9ECFF),
                onSecondaryContainer = Blue,
            )
    fun start(list: List<Card>, mode: String) {
        if (list.isEmpty()) vm.message.value = "Chưa có thẻ phù hợp để học."
        else {
            session = list
            sessionMode = mode
            page = "Buổi học"
        }
    }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                vm.run {
                    activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(vm.backup())
                    }
                    vm.message.value = "Đã lưu bản sao lưu."
                }
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                confirm =
                    "Thay thế toàn bộ dữ liệu bằng bản sao lưu này?" to
                        {
                            vm.run {
                                val text =
                                    activity.contentResolver
                                        .openInputStream(uri)!!
                                        .bufferedReader()
                                        .use { it.readText() }
                                vm.restore(text)
                                dark = prefs.getBoolean("dark", false)
                            }
                        }
        }
    BackHandler(page !in listOf("Trang chủ", "Thư viện", "Thống kê", "Cài đặt")) {
        page = if (page == "Buổi học") "Chi tiết" else "Thư viện"
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                if (page !in listOf("Buổi học", "Nhập"))
                    NavigationBar(containerColor = colors.surface) {
                        listOf("Trang chủ", "Thư viện", "Thống kê", "Cài đặt").forEachIndexed { i, p
                            ->
                            NavigationBarItem(
                                selected = page == p || (page == "Chi tiết" && p == "Thư viện"),
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedTextColor = colors.primary,
                                        selectedIconColor = colors.primary,
                                    ),
                                onClick = {
                                    page = p
                                    folderId = null
                                },
                                icon = { Text(listOf("⌂", "▤", "▥", "⚙")[i], fontSize = 22.sp) },
                                label = { Text(p, fontSize = 11.sp) },
                            )
                        }
                    }
            },
        ) { padding ->
            if (page == "Buổi học") {
                Box(Modifier.padding(padding)) {
                    StudySession(session, sessionMode, vm, activity) {
                        page = if (setId == null) "Trang chủ" else "Chi tiết"
                    }
                }
            } else if (page == "Nhập" && importSet != null) {
                Box(Modifier.padding(padding)) {
                    ImportScreen(importSet!!, cards, activity, { page = "Chi tiết" }) { s, rows ->
                        vm.import(s, rows)
                        setId = s.id
                        page = "Chi tiết"
                    }
                }
            } else
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 20.dp),
                ) {
                    when (page) {
                        "Trang chủ" -> {
                            val due =
                                cards.filter { c ->
                                    progress.find { it.cardId == c.id }?.due?.let { it <= today() }
                                        ?: true
                                }
                            val learned =
                                logs
                                    .filter { it.day == today() && it.first }
                                    .map { it.cardId }
                                    .distinct()
                                    .size
                            val goal = prefs.getInt("goal", 20)
                            item {
                                Text("Chào bạn,", color = colors.onSurfaceVariant)
                                Title(
                                    "Hôm nay học gì?",
                                    "${streak(logs)} ngày liên tiếp · WordDeck",
                                )
                            }
                            item {
                                Panel {
                                    Text("Ôn tập hôm nay", fontWeight = FontWeight.Bold)
                                    Text("${due.size} từ đến hạn")
                                    LinearProgressIndicator(
                                        progress = {
                                            if (cards.isEmpty()) 0f
                                            else 1f - due.size.toFloat() / cards.size
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Action("Bắt đầu ôn", due.isNotEmpty()) { start(due, "Học") }
                                }
                            }
                            item {
                                Panel {
                                    Text("Mục tiêu từ mới", fontWeight = FontWeight.Bold)
                                    Text("$learned / $goal từ hôm nay")
                                    LinearProgressIndicator(
                                        progress = { (learned.toFloat() / goal).coerceIn(0f, 1f) },
                                        color = Green,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            item {
                                Title("Thư mục")
                                TextButton({ page = "Thư viện" }) { Text("Xem tất cả →") }
                            }
                            items(folders) { f ->
                                Panel {
                                    val folderCards =
                                        cards.filter { c ->
                                            sets.any { it.id == c.setId && it.folderId == f.id }
                                        }
                                    val remembered =
                                        folderCards.count { c ->
                                            progress.any { it.cardId == c.id && it.interval >= 6 }
                                        }
                                    TextButton({
                                        folderId = f.id
                                        page = "Thư viện"
                                    }) {
                                        Text(f.name, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        "${sets.count{it.folderId==f.id}} học phần · ${cards.count{c->sets.any{it.id==c.setId&&it.folderId==f.id}}} từ"
                                    )
                                    Text(
                                        "${if(folderCards.isEmpty())0 else remembered*100/folderCards.size}% nhớ tốt",
                                        fontSize = 12.sp,
                                        color = Blue,
                                    )
                                }
                            }
                            if (folders.isEmpty())
                                item {
                                    Panel {
                                        Text("Bắt đầu với bộ từ của bạn")
                                        Text(
                                            "Tạo thư mục rồi dán danh sách từ để học mọi lúc, kể cả khi mất mạng."
                                        )
                                        Action("Tạo thư mục đầu tiên") {
                                            folderEdit = Folder(name = "")
                                        }
                                    }
                                }
                        }
                        "Thư viện" -> {
                            item {
                                Title(folders.find { it.id == folderId }?.name ?: "Thư viện")
                                Field(search, "Tìm thư mục, học phần, từ vựng") { search = it }
                                Row {
                                    TextButton({ folderEdit = Folder(name = "") }) {
                                        Text("+ Thư mục")
                                    }
                                    if (folderId != null)
                                        TextButton({
                                            setEdit = StudySet(folderId = folderId!!, name = "")
                                        }) {
                                            Text("+ Học phần")
                                        }
                                }
                            }
                            if (folderId != null)
                                item {
                                    TextButton({ folderId = null }) { Text("← Tất cả thư mục") }
                                }
                            if (folderId == null)
                                items(folders.filter { it.name.contains(search, true) }) { f ->
                                    Panel {
                                        TextButton({ folderId = f.id }) {
                                            Text(f.name, fontWeight = FontWeight.Bold)
                                        }
                                        Text(f.description)
                                        Row {
                                            TextButton({ folderEdit = f }) { Text("Sửa") }
                                            TextButton({
                                                confirm =
                                                    "Xóa thư mục và tất cả học phần, thẻ bên trong?" to
                                                        {
                                                            vm.delete(f)
                                                        }
                                            }) {
                                                Text("Xóa")
                                            }
                                        }
                                    }
                                }
                            items(
                                sets.filter {
                                    (folderId == null || it.folderId == folderId) &&
                                        (it.name.contains(search, true) ||
                                            cards.any { c ->
                                                c.setId == it.id &&
                                                    (c.word.contains(search, true) ||
                                                        c.meaning.contains(search, true))
                                            })
                                }
                            ) { s ->
                                Panel {
                                    TextButton({
                                        setId = s.id
                                        stars = false
                                        page = "Chi tiết"
                                    }) {
                                        Text(s.name, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        "${cards.count{it.setId==s.id}} từ · ${cards.count{c->c.setId==s.id&&progress.any{it.cardId==c.id&&it.interval>=6}}} từ nhớ tốt"
                                    )
                                    Text(s.description)
                                }
                            }
                        }
                        "Chi tiết" -> {
                            val s = sets.find { it.id == setId }
                            val list = cards.filter { it.setId == setId && (!stars || it.starred) }
                            if (s != null) {
                                item {
                                    Row {
                                        TextButton({ page = "Thư viện" }) { Text("← Quay lại") }
                                        TextButton({ setEdit = s }) { Text("Sửa") }
                                        TextButton({
                                            confirm =
                                                "Xóa học phần và toàn bộ thẻ?" to
                                                    {
                                                        vm.delete(s)
                                                        page = "Thư viện"
                                                    }
                                        }) {
                                            Text("Xóa")
                                        }
                                    }
                                    Title(s.name, "${list.size} từ · ${s.description}")
                                }
                                item {
                                    Panel {
                                        Action("Học") { start(list, "Học") }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                        ) {
                                            TextButton({ start(list, "Luyện tập") }) {
                                                Text("Luyện tập")
                                            }
                                            TextButton({ start(list, "Kiểm tra") }) {
                                                Text("Kiểm tra")
                                            }
                                            TextButton({ start(list, "Ghép cặp") }) {
                                                Text("Ghép cặp")
                                            }
                                        }
                                        Action("Ôn từ sai") {
                                            start(
                                                list.filter { c ->
                                                    progress.any {
                                                        it.cardId == c.id && it.mistakes > 0
                                                    }
                                                },
                                                "Học",
                                            )
                                        }
                                    }
                                }
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Chỉ từ gắn sao", Modifier.weight(1f))
                                        Switch(stars, { stars = it })
                                    }
                                    Row {
                                        TextButton({ cardEdit = Card(setId = s.id, word = "") }) {
                                            Text("+ Thêm thẻ")
                                        }
                                        TextButton({
                                            importSet = s
                                            page = "Nhập"
                                        }) {
                                            Text("Dán / nhập file")
                                        }
                                    }
                                }
                                items(list, key = { it.id }) { c ->
                                    var menu by remember { mutableStateOf(false) }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = colors.surface,
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(c.word, fontWeight = FontWeight.Bold)
                                                if (c.phonetic.isNotBlank() || c.kind.isNotBlank())
                                                    Text(
                                                        "${c.phonetic} ${c.kind}",
                                                        fontSize = 12.sp,
                                                        color = colors.onSurfaceVariant,
                                                    )
                                                Text(c.meaning, fontSize = 14.sp)
                                            }
                                            TextButton({ vm.save(c.copy(starred = !c.starred)) }) {
                                                Text(
                                                    if (c.starred) "★" else "☆",
                                                    color = Color(0xFFF18B32),
                                                    fontSize = 24.sp,
                                                )
                                            }
                                            Box {
                                                TextButton({ menu = true }) {
                                                    Text("⋮", fontSize = 22.sp)
                                                }
                                                DropdownMenu(
                                                    expanded = menu,
                                                    onDismissRequest = { menu = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Nghe") },
                                                        onClick = {
                                                            menu = false
                                                            activity.speak(c.word)
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Sửa") },
                                                        onClick = {
                                                            menu = false
                                                            cardEdit = c
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Xóa") },
                                                        onClick = {
                                                            menu = false
                                                            confirm =
                                                                "Xóa thẻ ${c.word}?" to
                                                                    {
                                                                        vm.delete(c)
                                                                    }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (list.isEmpty())
                                    item {
                                        Text("Chưa có thẻ. Thêm hoặc nhập danh sách để bắt đầu.")
                                    }
                            } else
                                item { TextButton({ page = "Thư viện" }) { Text("Về thư viện") } }
                        }
                        "Thống kê" -> {
                            item { Title("Tiến bộ của bạn", "Mỗi ngày một chút, nhớ lâu hơn.") }
                            item {
                                Panel {
                                    Text("🔥 ${streak(logs)} ngày liên tiếp", fontSize = 22.sp)
                                    Text(
                                        "${progress.size} từ đã học · ${progress.count{it.interval>=6}} từ đang nhớ tốt"
                                    )
                                    Text(
                                        "${logs.size} lượt trả lời · ${if(logs.isEmpty())0 else logs.count{it.correct}*100/logs.size}% chính xác"
                                    )
                                }
                            }
                            item {
                                Panel {
                                    Text("7 ngày gần nhất", fontWeight = FontWeight.Bold)
                                    val counts =
                                        (6 downTo 0).map { d ->
                                            logs.count { it.day == today() - d }
                                        }
                                    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                                    Row(
                                        Modifier.fillMaxWidth().height(160.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.Bottom,
                                    ) {
                                        counts.forEachIndexed { i, n ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("$n", fontSize = 12.sp)
                                                Box(
                                                    Modifier.width(22.dp)
                                                        .height((100f * n / max + 3).dp)
                                                        .background(Blue, RoundedCornerShape(5.dp))
                                                )
                                                Text(
                                                    java.time.LocalDate.now()
                                                        .minusDays((6 - i).toLong())
                                                        .dayOfMonth
                                                        .toString(),
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Action("Ôn tất cả từ sai") {
                                    start(
                                        cards.filter { c ->
                                            progress.any { it.cardId == c.id && it.mistakes > 0 }
                                        },
                                        "Học",
                                    )
                                }
                            }
                        }
                        "Cài đặt" ->
                            item {
                                Settings(
                                    activity,
                                    dark,
                                    {
                                        dark = it
                                        prefs.edit().putBoolean("dark", it).apply()
                                    },
                                    { export.launch("WordDeck-${java.time.LocalDate.now()}.json") },
                                    { restore.launch(arrayOf("application/json", "text/*")) },
                                )
                            }
                    }
                }
        }
        folderEdit?.let { f ->
            NameDialog("Thư mục", f.name, f.description, { folderEdit = null }) { n, d ->
                vm.save(f.copy(name = n, description = d))
                folderEdit = null
            }
        }
        setEdit?.let { s ->
            NameDialog("Học phần", s.name, s.description, { setEdit = null }) { n, d ->
                val updated = s.copy(name = n, description = d)
                vm.save(updated)
                setEdit = null
                setId = s.id
                page = "Chi tiết"
            }
        }
        cardEdit?.let { c ->
            CardDialog(c, { cardEdit = null }) {
                vm.save(it)
                cardEdit = null
            }
        }
        confirm?.let { (text, action) ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text("Xác nhận") },
                text = { Text(text) },
                confirmButton = {
                    TextButton({
                        action()
                        confirm = null
                    }) {
                        Text("Tiếp tục")
                    }
                },
                dismissButton = { TextButton({ confirm = null }) { Text("Hủy") } },
            )
        }
    }
}

fun streak(logs: List<StudyLog>): Int {
    val days = logs.map { it.day }.toSet()
    var d = if (today() in days) today() else today() - 1
    var n = 0
    while (d in days) {
        n++
        d--
    }
    return n
}

@Composable
fun NameDialog(
    title: String,
    name: String,
    description: String,
    close: () -> Unit,
    save: (String, String) -> Unit,
) {
    var n by remember { mutableStateOf(name) }
    var d by remember { mutableStateOf(description) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = {
            Column {
                Field(n, "Tên") { n = it }
                Field(d, "Mô tả") { d = it }
            }
        },
        confirmButton = {
            TextButton({ save(n.trim(), d.trim()) }, enabled = n.isNotBlank()) { Text("Lưu") }
        },
        dismissButton = { TextButton(close) { Text("Hủy") } },
    )
}

@Composable
fun CardDialog(card: Card, close: () -> Unit, save: (Card) -> Unit) {
    var c by remember { mutableStateOf(card) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Chỉnh sửa thẻ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(c.word, "Từ / cụm từ") { c = c.copy(word = it) }
                Field(c.meaning, "Nghĩa tiếng Việt") { c = c.copy(meaning = it) }
                Field(c.phonetic, "Phiên âm") { c = c.copy(phonetic = it) }
                Field(c.kind, "Loại từ") { c = c.copy(kind = it) }
                Field(c.example, "Câu ví dụ") { c = c.copy(example = it) }
            }
        },
        confirmButton = {
            TextButton(
                { save(c.copy(word = c.word.trim(), meaning = c.meaning.trim())) },
                enabled = c.word.isNotBlank() && c.meaning.isNotBlank(),
            ) {
                Text("Lưu")
            }
        },
        dismissButton = { TextButton(close) { Text("Hủy") } },
    )
}

@Composable
fun Settings(
    a: MainActivity,
    dark: Boolean,
    toggle: (Boolean) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
) {
    val p = remember { a.getSharedPreferences("settings", 0) }
    var goal by remember { mutableStateOf(p.getInt("goal", 20).toString()) }
    var reminder by remember { mutableStateOf(p.getBoolean("reminder", false)) }
    var hour by remember { mutableStateOf(p.getInt("hour", 20)) }
    var minute by remember { mutableStateOf(p.getInt("minute", 0)) }
    var voice by remember { mutableStateOf(p.getString("voice", "US") ?: "US") }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (!ok) {
                reminder = false
                p.edit().putBoolean("reminder", false).apply()
                scheduleReminder(a)
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Title("Cài đặt")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chế độ tối", Modifier.weight(1f))
                Switch(dark, toggle)
            }
            Field(goal, "Mục tiêu từ mới mỗi ngày (1–500)") { goal = it }
            Action("Lưu mục tiêu", goal.toIntOrNull() in 1..500) {
                p.edit().putInt("goal", goal.toInt()).apply()
                Toast.makeText(a, "Đã lưu mục tiêu", Toast.LENGTH_SHORT).show()
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nhắc học", Modifier.weight(1f))
                Switch(
                    reminder,
                    {
                        reminder = it
                        p.edit().putBoolean("reminder", it).apply()
                        if (it && android.os.Build.VERSION.SDK_INT >= 33)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        scheduleReminder(a)
                    },
                )
            }
            TextButton({
                android.app
                    .TimePickerDialog(
                        a,
                        { _, h, m ->
                            hour = h
                            minute = m
                            p.edit().putInt("hour", h).putInt("minute", m).apply()
                            scheduleReminder(a)
                        },
                        hour,
                        minute,
                        true,
                    )
                    .show()
            }) {
                Text("Giờ nhắc: %02d:%02d".format(hour, minute))
            }
            Text("Android có thể gửi nhắc muộn khi máy tiết kiệm pin.", fontSize = 12.sp)
        }
        Panel {
            Text("Giọng đọc tiếng Anh")
            Row {
                listOf("US", "UK").forEach { v ->
                    FilterChip(
                        selected = voice == v,
                        onClick = {
                            voice = v
                            p.edit().putString("voice", v).apply()
                        },
                        label = { Text(v) },
                    )
                }
            }
            TextButton({ a.speak("Welcome to WordDeck") }) { Text("Nghe thử") }
        }
        Panel {
            Text("Dữ liệu của bạn", fontWeight = FontWeight.Bold)
            Action("Sao lưu ra file JSON", onClick = backup)
            OutlinedButton(restore, modifier = Modifier.fillMaxWidth()) {
                Text("Khôi phục từ file")
            }
            Text(
                "Không tài khoản · Không quảng cáo\nTừ đã lưu có thể học offline. Tự điền cần mạng để tải mô hình dịch lần đầu.",
                fontSize = 12.sp,
            )
        }
    }
}
