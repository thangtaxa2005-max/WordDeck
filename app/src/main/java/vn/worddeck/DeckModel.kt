package vn.worddeck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.*

class DeckModel(app: Application) : AndroidViewModel(app) {
    val db = DeckDatabase.open(app)
    val dao = db.dao()
    val folders = dao.folders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sets = dao.sets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cards = dao.cards().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress = dao.progress().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs = dao.logs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableStateFlow("")

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                message.value = e.message ?: "Không thể thực hiện."
            }
        }
    }

    fun save(f: Folder) = run { dao.folder(f) }

    fun save(s: StudySet) = run { dao.set(s) }

    fun save(c: Card) = run { dao.cards(listOf(c)) }

    fun import(s: StudySet, rows: List<Card>) = run {
        db.withTransaction {
            dao.set(s)
            dao.cards(rows)
        }
    }

    private suspend fun removeCard(c: Card) {
        dao.deleteLogs(c.id)
        dao.deleteProgress(c.id)
        dao.deleteCard(c.id)
    }

    private suspend fun removeSet(s: StudySet) {
        dao.cards().first().filter { it.setId == s.id }.forEach { removeCard(it) }
        dao.deleteSet(s.id)
    }

    fun delete(c: Card) = run { db.withTransaction { removeCard(c) } }

    fun delete(s: StudySet) = run { db.withTransaction { removeSet(s) } }

    fun delete(f: Folder) = run {
        db.withTransaction {
            dao.sets().first().filter { it.folderId == f.id }.forEach { removeSet(it) }
            dao.deleteFolder(f.id)
        }
    }

    suspend fun rate(c: Card, grade: Int) {
        db.withTransaction {
            val old = dao.progress().first().find { it.cardId == c.id }
            dao.progress(Scheduler.rate(old ?: Progress(c.id), grade))
            dao.log(StudyLog(cardId = c.id, correct = grade > 0, first = old == null))
        }
    }

    suspend fun backup(): String =
        db.withTransaction {
            val folderSnapshot = dao.folders().first()
            val setSnapshot = dao.sets().first()
            val cardSnapshot = dao.cards().first()
            val progressSnapshot = dao.progress().first()
            val logSnapshot = dao.logs().first()
            fun arr(values: List<JSONObject>) = JSONArray(values)
            JSONObject()
                .put("version", 1)
                .put(
                    "folders",
                    arr(
                        folderSnapshot.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("name", it.name)
                                .put("description", it.description)
                        }
                    ),
                )
                .put(
                    "sets",
                    arr(
                        setSnapshot.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("folderId", it.folderId)
                                .put("name", it.name)
                                .put("description", it.description)
                        }
                    ),
                )
                .put(
                    "cards",
                    arr(
                        cardSnapshot.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("setId", it.setId)
                                .put("word", it.word)
                                .put("meaning", it.meaning)
                                .put("phonetic", it.phonetic)
                                .put("kind", it.kind)
                                .put("example", it.example)
                                .put("starred", it.starred)
                        }
                    ),
                )
                .put(
                    "progress",
                    arr(
                        progressSnapshot.map {
                            JSONObject()
                                .put("cardId", it.cardId)
                                .put("ease", it.ease)
                                .put("interval", it.interval)
                                .put("repetitions", it.repetitions)
                                .put("due", it.due)
                                .put("mistakes", it.mistakes)
                        }
                    ),
                )
                .put(
                    "logs",
                    arr(
                        logSnapshot.map {
                            JSONObject()
                                .put("id", it.id)
                                .put("cardId", it.cardId)
                                .put("day", it.day)
                                .put("correct", it.correct)
                                .put("first", it.first)
                        }
                    ),
                )
                .put(
                    "settings",
                    JSONObject(
                        getApplication<Application>().getSharedPreferences("settings", 0).all
                    ),
                )
                .toString(2)
        }

    suspend fun restore(text: String) {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Phiên bản sao lưu không được hỗ trợ." }
        fun rows(key: String): List<JSONObject> {
            val a = root.getJSONArray(key)
            require(a.length() <= 100000)
            return (0 until a.length()).map { a.getJSONObject(it) }
        }
        val fs =
            rows("folders").map {
                Folder(it.getString("id"), it.getString("name"), it.optString("description"))
            }
        val ss =
            rows("sets").map {
                StudySet(
                    it.getString("id"),
                    it.getString("folderId"),
                    it.getString("name"),
                    it.optString("description"),
                )
            }
        val cs =
            rows("cards").map {
                Card(
                    it.getString("id"),
                    it.getString("setId"),
                    it.getString("word"),
                    it.getString("meaning"),
                    it.optString("phonetic"),
                    it.optString("kind"),
                    it.optString("example"),
                    it.optBoolean("starred"),
                )
            }
        val ps =
            rows("progress").map {
                Progress(
                    it.getString("cardId"),
                    it.getDouble("ease"),
                    it.getInt("interval"),
                    it.getInt("repetitions"),
                    it.getLong("due"),
                    it.getInt("mistakes"),
                )
            }
        val ls =
            rows("logs").map {
                StudyLog(
                    it.getString("id"),
                    it.getString("cardId"),
                    it.getLong("day"),
                    it.getBoolean("correct"),
                    it.optBoolean("first"),
                )
            }
        require(
            fs.map { it.id }.distinct().size == fs.size &&
                ss.map { it.id }.distinct().size == ss.size &&
                cs.map { it.id }.distinct().size == cs.size
        ) {
            "ID bị trùng trong bản sao lưu."
        }
        require(
            ss.all { s -> fs.any { it.id == s.folderId } } &&
                cs.all { c -> ss.any { it.id == c.setId } } &&
                ps.all { p -> cs.any { it.id == p.cardId } } &&
                ls.all { l -> cs.any { it.id == l.cardId } }
        ) {
            "Bản sao lưu thiếu dữ liệu liên kết."
        }
        require(
            ps.all {
                it.ease.isFinite() &&
                    it.ease >= 1.3 &&
                    it.interval >= 0 &&
                    it.repetitions >= 0 &&
                    it.mistakes >= 0
            }
        ) {
            "Lịch ôn không hợp lệ."
        }
        val settings = root.optJSONObject("settings") ?: JSONObject()
        require(
            settings.optInt("goal", 20) in 1..500 &&
                settings.optInt("hour", 20) in 0..23 &&
                settings.optInt("minute", 0) in 0..59
        ) {
            "Cài đặt trong bản sao lưu không hợp lệ."
        }
        require(
            cs.all { it.word.isNotBlank() && it.meaning.isNotBlank() } &&
                fs.all { it.name.isNotBlank() } &&
                ss.all { it.name.isNotBlank() }
        ) {
            "Bản sao lưu có nội dung trống."
        }
        db.withTransaction {
            dao.clearLogs()
            dao.clearProgress()
            dao.clearCards()
            dao.clearSets()
            dao.clearFolders()
            fs.forEach { dao.folder(it) }
            ss.forEach { dao.set(it) }
            dao.cards(cs)
            ps.forEach { dao.progress(it) }
            ls.forEach { dao.log(it) }
        }
        val edit = getApplication<Application>().getSharedPreferences("settings", 0).edit().clear()
        edit
            .putInt("goal", settings.optInt("goal", 20))
            .putInt("hour", settings.optInt("hour", 20))
            .putInt("minute", settings.optInt("minute", 0))
            .putBoolean("dark", settings.optBoolean("dark"))
            .putBoolean("reminder", settings.optBoolean("reminder"))
            .putString("voice", if (settings.optString("voice") == "UK") "UK" else "US")
        settings.keys().forEach { k ->
            if (k.startsWith("record_") && settings.optInt(k, -1) >= 0)
                edit.putInt(k, settings.getInt(k))
        }
        edit.apply()
        scheduleReminder(getApplication())
        message.value = "Đã khôi phục dữ liệu."
    }
}
