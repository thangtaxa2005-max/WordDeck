package vn.worddeck

import android.content.Context
import androidx.room.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

fun uid() = UUID.randomUUID().toString()

fun today() = LocalDate.now().toEpochDay()

@Entity
data class Folder(
    @PrimaryKey val id: String = uid(),
    val name: String,
    val description: String = "",
)

@Entity
data class StudySet(
    @PrimaryKey val id: String = uid(),
    val folderId: String,
    val name: String,
    val description: String = "",
)

@Entity
data class Card(
    @PrimaryKey val id: String = uid(),
    val setId: String,
    val word: String,
    val meaning: String = "",
    val phonetic: String = "",
    val kind: String = "",
    val example: String = "",
    val starred: Boolean = false,
)

@Entity
data class Progress(
    @PrimaryKey val cardId: String,
    val ease: Double = 2.5,
    val interval: Int = 0,
    val repetitions: Int = 0,
    val due: Long = today(),
    val mistakes: Int = 0,
)

@Entity
data class StudyLog(
    @PrimaryKey val id: String = uid(),
    val cardId: String,
    val day: Long = today(),
    val correct: Boolean,
    val first: Boolean = false,
)

@Dao
interface DeckDao {
    @Query("SELECT * FROM Folder ORDER BY name") fun folders(): Flow<List<Folder>>

    @Query("SELECT * FROM StudySet ORDER BY name") fun sets(): Flow<List<StudySet>>

    @Query("SELECT * FROM Card ORDER BY word") fun cards(): Flow<List<Card>>

    @Query("SELECT * FROM Progress") fun progress(): Flow<List<Progress>>

    @Query("SELECT * FROM StudyLog") fun logs(): Flow<List<StudyLog>>

    @Upsert suspend fun folder(v: Folder)

    @Upsert suspend fun set(v: StudySet)

    @Upsert suspend fun cards(v: List<Card>)

    @Upsert suspend fun progress(v: Progress)

    @Upsert suspend fun log(v: StudyLog)

    @Query("DELETE FROM Folder WHERE id=:id") suspend fun deleteFolder(id: String)

    @Query("DELETE FROM StudySet WHERE id=:id") suspend fun deleteSet(id: String)

    @Query("DELETE FROM Card WHERE id=:id") suspend fun deleteCard(id: String)

    @Query("DELETE FROM Progress WHERE cardId=:id") suspend fun deleteProgress(id: String)

    @Query("DELETE FROM StudyLog WHERE cardId=:id") suspend fun deleteLogs(id: String)

    @Query("DELETE FROM Folder") suspend fun clearFolders()

    @Query("DELETE FROM StudySet") suspend fun clearSets()

    @Query("DELETE FROM Card") suspend fun clearCards()

    @Query("DELETE FROM Progress") suspend fun clearProgress()

    @Query("DELETE FROM StudyLog") suspend fun clearLogs()
}

@Database(
    entities = [Folder::class, StudySet::class, Card::class, Progress::class, StudyLog::class],
    version = 1,
    exportSchema = false,
)
abstract class DeckDatabase : RoomDatabase() {
    abstract fun dao(): DeckDao

    companion object {
        fun open(c: Context) =
            Room.databaseBuilder(c, DeckDatabase::class.java, "worddeck.db")
                .enableMultiInstanceInvalidation()
                .build()
    }
}

object Scheduler {
    fun rate(old: Progress, grade: Int, day: Long = today()): Progress {
        require(grade in 0..3)
        val quality = listOf(1, 3, 4, 5)[grade]
        val ease =
            (old.ease + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)).coerceAtLeast(1.3)
        val reps = if (grade == 0) 0 else old.repetitions + 1
        val interval =
            if (grade == 0) 0
            else if (old.repetitions == 0) 1
            else if (old.repetitions == 1) 6
            else kotlin.math.round(old.interval * ease).toInt().coerceAtLeast(1)
        return old.copy(
            ease = ease,
            interval = interval,
            repetitions = reps,
            due = day + interval,
            mistakes = old.mistakes + if (grade == 0) 1 else 0,
        )
    }
}

object Importer {
    // RFC 4180 quoted fields, including embedded newlines and escaped quotes.
    fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    row.add(field.toString().trim())
                    field.clear()
                }
                c == '\n' && !quoted -> {
                    row.add(field.toString().trim())
                    rows.add(row)
                    row = mutableListOf()
                    field.clear()
                }
                c != '\r' -> field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV có dấu ngoặc kép chưa đóng." }
        row.add(field.toString().trim())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows.filter { it.any(String::isNotBlank) }
    }

    fun parse(text: String, setId: String, isCsv: Boolean = false): List<Card> {
        val rows =
            if (isCsv) csv(text.removePrefix("\uFEFF"))
            else
                text
                    .lines()
                    .filter { it.isNotBlank() }
                    .map { it.split(Regex("\\t|\\s+-\\s+"), limit = 2).map(String::trim) }
        return rows
            .filterIndexed { i, r ->
                !(isCsv && i == 0 && r.first().lowercase() in listOf("word", "từ"))
            }
            .filter { it[0].isNotBlank() }
            .map { r ->
                Card(
                    setId = setId,
                    word = r[0],
                    meaning = r.getOrElse(1) { "" },
                    phonetic = r.getOrElse(2) { "" },
                    kind = r.getOrElse(3) { "" },
                    example = r.getOrElse(4) { "" },
                )
            }
    }
}
