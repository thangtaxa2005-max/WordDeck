package vn.worddeck

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun normalized(s: String) =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFKC).replace(Regex("\\s+"), " ")

data class Answer(val card: Card, val input: String, val correct: Boolean)

val modes = listOf("Trắc nghiệm", "Điền từ", "Nghe rồi gõ", "Write", "Đúng / sai")

@Composable
fun StudySession(
    source: List<Card>,
    mode: String,
    vm: DeckModel,
    a: MainActivity,
    close: () -> Unit,
) {
    var configured by remember { mutableStateOf(mode == "Học" || mode == "Ghép cặp") }
    var count by remember { mutableStateOf(source.size.coerceAtMost(20).toString()) }
    var timed by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf("5") }
    var selected by remember { mutableStateOf(setOf(modes[0])) }
    var queue by remember { mutableStateOf(source.shuffled()) }
    var reverse by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf<List<Answer>>(emptyList()) }
    var finished by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var started by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var elapsed by remember { mutableIntStateOf(0) }
    var resultSeconds by remember { mutableIntStateOf(0) }
    var retry by remember { mutableStateOf<List<Card>>(emptyList()) }
    var questionTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun finish() {
        resultSeconds = elapsed
        finished = true
    }
    LaunchedEffect(configured, finished) {
        while (configured && !finished) {
            elapsed = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
            if (
                timed && mode == "Kiểm tra" && !busy && elapsed >= (minutes.toIntOrNull() ?: 5) * 60
            ) {
                busy = true
                val remaining = queue.drop(index + (if (feedback != null) 1 else 0))
                answers = answers + remaining.map { Answer(it, "Chưa trả lời", false) }
                try {
                    remaining.forEach { vm.rate(it, 0) }
                } catch (e: Exception) {
                    vm.message.value = "Không lưu được một phần tiến độ: ${e.message}"
                } finally {
                    finish()
                    busy = false
                }
            }
            delay(250)
        }
    }
    if (mode == "Ghép cặp") {
        MatchGame(source, a, close)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(close) { Text("Đóng") }
            Spacer(Modifier.weight(1f))
            Text(if (configured) "${(index+1).coerceAtMost(queue.size)} / ${queue.size}" else mode)
            Spacer(Modifier.weight(1f))
            if (mode == "Học")
                TextButton({
                    reverse = !reverse
                    flipped = false
                }) {
                    Text(if (reverse) "Việt → Anh" else "Anh → Việt")
                }
        }
        if (!configured) {
            Title(
                if (mode == "Kiểm tra") "Tạo bài kiểm tra" else "Luyện tập",
                "Chọn dạng câu hỏi để bắt đầu",
            )
            if (mode == "Kiểm tra") Field(count, "Số câu (1–${source.size})") { count = it }
            modes.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selected = if (m in selected) selected - m else selected + m
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(m in selected, null)
                    Text(m)
                }
            }
            if (mode == "Kiểm tra") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(timed, { timed = it })
                    Text("Giới hạn thời gian")
                }
                if (timed) Field(minutes, "Số phút (1–120)") { minutes = it }
            }
            Action(
                "Bắt đầu",
                selected.isNotEmpty() &&
                    (mode != "Kiểm tra" || count.toIntOrNull() in 1..source.size) &&
                    (!timed || minutes.toIntOrNull() in 1..120),
            ) {
                queue =
                    source.shuffled().take(if (mode == "Kiểm tra") count.toInt() else source.size)
                questionTypes = queue.indices.map { selected.toList()[it % selected.size] }
                started = SystemClock.elapsedRealtime()
                configured = true
            }
            return@Column
        }
        if (finished) {
            val correct = answers.count { it.correct }
            val total = answers.size
            val percent = if (total == 0) 0 else correct * 100 / total
            Title(if (mode == "Học") "Hoàn thành buổi học" else "Kết quả kiểm tra")
            Panel {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.size(150.dp),
                        color = Green,
                        strokeWidth = 12.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$percent%", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("$correct / $total đúng")
                    }
                }
                Text("Thời gian: ${resultSeconds/60}:%02d".format(resultSeconds % 60))
                Text(
                    "Điểm tốc độ: ${if(correct==0)0 else (correct*1000/(1+resultSeconds/30.0)).toInt()}"
                )
            }
            Text("Từ sai (${answers.count{!it.correct}})", fontWeight = FontWeight.Bold)
            answers
                .filter { !it.correct }
                .distinctBy { it.card.id }
                .forEach { r ->
                    Panel {
                        Text(r.card.word, fontWeight = FontWeight.Bold)
                        Text("Đáp án: ${r.card.meaning}")
                        Text("Bạn chọn: ${r.input}")
                    }
                }
            fun reset(list: List<Card>) {
                queue = list
                questionTypes = queue.indices.map { selected.toList()[it % selected.size] }
                index = 0
                answers = emptyList()
                finished = false
                feedback = null
                input = ""
                flipped = false
                retry = emptyList()
                started = SystemClock.elapsedRealtime()
                elapsed = 0
            }
            Action("Ôn từ sai", answers.any { !it.correct }) {
                reset(answers.filter { !it.correct }.map { it.card }.distinctBy { it.id })
            }
            OutlinedButton(
                {
                    reset(
                        source
                            .shuffled()
                            .take(if (mode == "Kiểm tra") count.toInt() else source.size)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Làm lại")
            }
            Action("Hoàn tất", onClick = close)
            return@Column
        }
        val c = queue.getOrNull(index) ?: return@Column
        LinearProgressIndicator(
            progress = { index.toFloat() / queue.size },
            modifier = Modifier.fillMaxWidth(),
        )
        if (timed) Text("Còn lại ${((minutes.toIntOrNull()?:5)*60-elapsed).coerceAtLeast(0)} giây")
        if (mode == "Học") {
            Surface(
                onClick = { flipped = !flipped },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(c.kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (flipped xor reverse) c.meaning else c.word,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(c.phonetic)
                    TextButton({ a.speak(c.word) }) { Text("Nghe phát âm") }
                    if (flipped && c.example.isNotBlank()) Text(c.example)
                    Spacer(Modifier.height(30.dp))
                    Text("Chạm để lật thẻ", fontSize = 12.sp)
                }
            }
            val latest by vm.cards.collectAsState()
            val starred = latest.find { it.id == c.id }?.starred ?: c.starred
            Row {
                TextButton({ vm.save(c.copy(starred = !starred)) }) {
                    Text(if (starred) "★ Đã gắn sao" else "☆ Gắn sao từ khó")
                }
                TextButton({
                    queue = queue.take(index) + queue.drop(index).shuffled()
                    flipped = false
                }) {
                    Text("Xáo trộn")
                }
            }
            Text("Đánh giá khả năng nhớ của bạn", fontSize = 13.sp)
            val schedule by vm.progress.collectAsState()
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Quên", "Khó", "Nhớ", "Dễ").forEachIndexed { grade, label ->
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    vm.rate(c, grade)
                                    answers = answers + Answer(c, label, grade > 0)
                                    flipped = false
                                    if (index + 1 >= queue.size) finish() else index++
                                } catch (e: Exception) {
                                    vm.message.value = "Không lưu được tiến độ: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    listOf(
                                        Color(0xFFD13F4B),
                                        Color(0xFFB86D19),
                                        Color(0xFF148574),
                                        Blue,
                                    )[grade]
                            ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label)
                            val interval =
                                Scheduler.rate(
                                        schedule.find { it.cardId == c.id } ?: Progress(c.id),
                                        grade,
                                    )
                                    .interval
                            Text(
                                if (interval == 0) "Hôm nay" else "$interval ngày",
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        } else {
            val type = questionTypes.getOrElse(index) { selected.first() }
            val choices =
                remember(c.id, index) {
                    (source
                            .filter { it.meaning != c.meaning }
                            .shuffled()
                            .map { it.meaning }
                            .distinct()
                            .take(3) + c.meaning)
                        .shuffled()
                }
            val shownMeaning =
                remember(c.id, index) {
                    if (kotlin.random.Random.nextBoolean()) c.meaning else source.random().meaning
                }
            fun submit(value: String, correct: Boolean) {
                if (
                    busy ||
                        feedback != null ||
                        finished ||
                        (timed && elapsed >= (minutes.toIntOrNull() ?: 5) * 60)
                )
                    return
                busy = true
                scope.launch {
                    try {
                        vm.rate(c, if (correct) 2 else 0)
                        input = value
                        feedback = correct
                        answers = answers + Answer(c, value, correct)
                        if (!correct && mode == "Luyện tập") retry = retry + c
                    } catch (e: Exception) {
                        vm.message.value = "Không lưu được câu trả lời: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
            Text(type, fontWeight = FontWeight.Bold)
            Panel {
                when (type) {
                    "Nghe rồi gõ" -> {
                        Text("Nghe và nhập từ tiếng Anh")
                        Action("▶ Nghe phát âm") { a.speak(c.word) }
                    }
                    "Write" -> Text(c.meaning, fontSize = 26.sp)
                    "Điền từ" -> {
                        Text(c.meaning, fontSize = 26.sp)
                        if (c.example.isNotBlank())
                            Text(
                                c.example.replace(
                                    Regex(Regex.escape(c.word), RegexOption.IGNORE_CASE),
                                    "_____",
                                )
                            )
                    }
                    "Đúng / sai" -> {
                        Text(c.word, fontSize = 26.sp)
                        Text(shownMeaning, fontSize = 22.sp)
                    }
                    else -> Text(c.word, fontSize = 28.sp)
                }
            }
            when (type) {
                "Trắc nghiệm" -> {
                    if (choices.size < 4)
                        Text(
                            "Bộ từ có ít hơn 4 nghĩa khác nhau; hiển thị các đáp án hiện có.",
                            fontSize = 12.sp,
                        )
                    choices.forEach { value ->
                        Action(value, !busy && feedback == null) {
                            submit(value, value == c.meaning)
                        }
                    }
                }
                "Đúng / sai" ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            { submit("Đúng", shownMeaning == c.meaning) },
                            enabled = !busy && feedback == null,
                        ) {
                            Text("Đúng")
                        }
                        Button(
                            { submit("Sai", shownMeaning != c.meaning) },
                            enabled = !busy && feedback == null,
                        ) {
                            Text("Sai")
                        }
                    }
                else -> {
                    Field(input, "Câu trả lời tiếng Anh") { if (feedback == null) input = it }
                    Action("Kiểm tra", !busy && feedback == null && input.isNotBlank()) {
                        submit(input, normalized(input) == normalized(c.word))
                    }
                }
            }
            feedback?.let { ok ->
                Panel {
                    Text(
                        if (ok) "Chính xác!" else "Chưa đúng",
                        color = if (ok) Green else MaterialTheme.colorScheme.error,
                    )
                    Text("${c.word} — ${c.meaning}")
                    Text(c.example)
                    Action("Tiếp tục", !busy) {
                        feedback = null
                        input = ""
                        if (index + 1 < queue.size) index++
                        else if (retry.isNotEmpty()) {
                            queue = queue + retry
                            questionTypes = questionTypes + retry.map { selected.random() }
                            retry = emptyList()
                            index++
                        } else finish()
                    }
                }
            }
        }
    }
}

@Composable
fun MatchGame(source: List<Card>, a: MainActivity, close: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val cards = remember(round) { source.shuffled().take(6) }
    val tiles =
        remember(round) {
            cards.flatMap { listOf(it.id to it.word, it.id + "_meaning" to it.meaning) }.shuffled()
        }
    var selected by remember(round) { mutableStateOf<String?>(null) }
    var matched by remember(round) { mutableStateOf<Set<String>>(emptySet()) }
    var seconds by remember(round) { mutableIntStateOf(0) }
    var penalty by remember(round) { mutableIntStateOf(0) }
    var text by remember(round) { mutableStateOf("Chọn một từ rồi chọn nghĩa tương ứng.") }
    val start = remember(round) { SystemClock.elapsedRealtime() }
    val prefs = remember { a.getSharedPreferences("settings", 0) }
    val key = remember(cards) { "record_" + cards.map { it.id }.sorted().joinToString("_") }
    var record by remember(key) { mutableIntStateOf(prefs.getInt(key, 0)) }
    val done = matched.size == tiles.size
    LaunchedEffect(round, done) {
        while (!done) {
            seconds = ((SystemClock.elapsedRealtime() - start) / 1000).toInt()
            delay(250)
        }
    }
    LaunchedEffect(done) {
        if (done && (record == 0 || seconds + penalty < record)) {
            record = seconds + penalty
            prefs.edit().putInt(key, record).apply()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(close) { Text("Đóng") }
        Title("Ghép cặp", "${matched.size/2} / ${cards.size} cặp · ${seconds+penalty} giây")
        Text(
            if (record == 0) "Chưa có kỷ lục cho nhóm từ này"
            else "Kỷ lục nhóm từ này: $record giây"
        )
        Text(text)
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (id, label) ->
                    OutlinedButton(
                        onClick = {
                            val prev = selected
                            if (prev == null) {
                                selected = id
                            } else if (prev == id) {
                                selected = null
                            } else {
                                if (
                                    prev.removeSuffix("_meaning") == id.removeSuffix("_meaning") &&
                                        prev.endsWith("_meaning") != id.endsWith("_meaning")
                                ) {
                                    matched = matched + prev + id
                                    text = "Đúng rồi!"
                                } else {
                                    penalty += 3
                                    text = "Chưa khớp, thử lại nhé (+3 giây)."
                                }
                                selected = null
                            }
                        },
                        enabled = id !in matched,
                        modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (selected == id) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                            ),
                    ) {
                        Text(if (id in matched) "✓" else label)
                    }
                }
            }
        }
        if (done) {
            Title("Hoàn thành!", "Thời gian: ${seconds+penalty} giây")
            Action("Chơi lại") { round++ }
            Action("Về học phần", onClick = close)
        }
    }
}
