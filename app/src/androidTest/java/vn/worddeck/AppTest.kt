package vn.worddeck

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var db: DeckDatabase

    @Before
    fun seed() {
        db = DeckDatabase.open(ui.activity)
        runBlocking {
            val d = db.dao()
            d.clearLogs()
            d.clearProgress()
            d.clearCards()
            d.clearSets()
            d.clearFolders()
            d.folder(Folder("f", "IELTS", "Từ vựng học thuật"))
            d.set(StudySet("s", "f", "IELTS Reading 1", "Bộ từ luyện tập"))
            d.cards(
                listOf(
                    Card(
                        "a",
                        "s",
                        "meticulous",
                        "tỉ mỉ",
                        "/məˈtɪkjələs/",
                        "adjective",
                        "She is meticulous.",
                        true,
                    ),
                    Card("b", "s", "reluctant", "miễn cưỡng"),
                    Card("c", "s", "substantial", "đáng kể"),
                    Card("d", "s", "diminish", "giảm bớt"),
                )
            )
        }
        ui.waitForIdle()
    }

    @After
    fun clean() {
        db.close()
    }

    private fun detail() {
        ui.onNodeWithText("Thư viện").performClick()
        ui.onNodeWithText("IELTS Reading 1").performScrollTo().performClick()
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val image =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .takeScreenshot()
        java.io.File(ui.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun flashcardsSaveScheduleAndStatistics() {
        screenshot("home")
        ui.onNodeWithText("Bắt đầu ôn").performClick()
        ui.onNodeWithText("Chạm để lật thẻ").assertExists()
        screenshot("flashcard")
        repeat(4) {
            ui.onNodeWithText("Nhớ", useUnmergedTree = true).performScrollTo().performClick()
            ui.waitForIdle()
        }
        ui.onNodeWithText("Hoàn thành buổi học").assertExists()
        runBlocking {
            assertEquals(4, db.dao().progress().first().size)
            assertEquals(4, db.dao().logs().first().size)
        }
    }

    @Test
    fun previewImportsAndRejectsDuplicates() {
        detail()
        ui.onNodeWithText("Dán / nhập file").performScrollTo().performClick()
        ui.onNodeWithText("Dán danh sách từ")
            .performScrollTo()
            .performTextInput("apple - quả táo\nbook - sách\napple - quả táo")
        ui.onNodeWithText("Xem trước").performScrollTo().performClick()
        ui.onNodeWithText("Loại tất cả từ trùng").performScrollTo().performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Lưu 2 thẻ"))
        ui.onNodeWithText("Lưu 2 thẻ").performClick()
        ui.waitUntil(5000) { runBlocking { db.dao().cards().first().size == 6 } }
    }

    @Test
    fun writeQuizShowsFeedbackAndResults() {
        detail()
        screenshot("study-set")
        ui.onNodeWithText("Kiểm tra", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Số câu (1–4)").performTextReplacement("1")
        ui.onNodeWithText("Trắc nghiệm").performClick()
        ui.onNodeWithText("Write").performClick()
        ui.onNodeWithText("Bắt đầu").performScrollTo().performClick()
        ui.onNodeWithText("Câu trả lời tiếng Anh")
            .performScrollTo()
            .performTextInput("wrong answer")
        ui.onNodeWithText("Kiểm tra").performScrollTo().performClick()
        ui.onNodeWithText("Chưa đúng").assertExists()
        ui.onNodeWithText("Tiếp tục").performScrollTo().performClick()
        ui.onNodeWithText("Kết quả kiểm tra").assertExists()
        ui.onNodeWithText("0%").assertExists()
        screenshot("result")
    }

    @Test
    fun matchGameCompletesAndPersistsRecord() {
        detail()
        ui.onNodeWithText("Ghép cặp").performClick()
        listOf(
                "meticulous" to "tỉ mỉ",
                "reluctant" to "miễn cưỡng",
                "substantial" to "đáng kể",
                "diminish" to "giảm bớt",
            )
            .forEach { (word, meaning) ->
                ui.onNodeWithText(word).performScrollTo().performClick()
                ui.onNodeWithText(meaning).performScrollTo().performClick()
            }
        ui.onNodeWithText("Hoàn thành!").assertExists()
        screenshot("match")
        assertTrue(
            ui.activity.getSharedPreferences("settings", 0).all.keys.any {
                it.startsWith("record_")
            }
        )
    }

    @Test
    fun backupRestoresAndInvalidBackupPreservesData() {
        val vm = DeckModel(ui.activity.application)
        runBlocking {
            val backup = vm.backup()
            db.dao().deleteCard("a")
            vm.restore(backup)
            assertEquals(4, db.dao().cards().first().size)
            try {
                vm.restore(backup.replace("\"folderId\": \"f\"", "\"folderId\": \"missing\""))
                fail("Should reject dangling relation")
            } catch (_: IllegalArgumentException) {}
            assertEquals(4, db.dao().cards().first().size)
        }
        vm.db.close()
    }

    @Test
    fun allPracticeModesAcceptAnswers() {
        detail()
        listOf("Trắc nghiệm", "Điền từ", "Nghe rồi gõ", "Đúng / sai").forEach { mode ->
            ui.onNodeWithText("Luyện tập").performScrollTo().performClick()
            if (mode != "Trắc nghiệm") {
                ui.onNodeWithText("Trắc nghiệm").performClick()
                ui.onNodeWithText(mode).performClick()
            }
            ui.onNodeWithText("Bắt đầu").performScrollTo().performClick()
            when (mode) {
                "Trắc nghiệm" -> ui.onNodeWithText("tỉ mỉ").performScrollTo().performClick()
                "Đúng / sai" ->
                    ui.onNodeWithText("Đúng", useUnmergedTree = true)
                        .performScrollTo()
                        .performClick()
                else -> {
                    ui.onNodeWithText("Câu trả lời tiếng Anh")
                        .performScrollTo()
                        .performTextInput("answer")
                    ui.onNodeWithText("Kiểm tra").performScrollTo().performClick()
                }
            }
            ui.onNodeWithText("Tiếp tục").assertExists()
            ui.onNodeWithText("Đóng").performScrollTo().performClick()
        }
        runBlocking { assertEquals(4, db.dao().logs().first().size) }
    }

    @Test
    fun createFolderSetAndCardThroughUi() {
        ui.onNodeWithText("Thư viện").performClick()
        ui.onNodeWithText("+ Thư mục").performClick()
        ui.onNodeWithText("Tên").performTextInput("Work")
        ui.onNodeWithText("Lưu").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Work"))
        ui.onNodeWithText("Work").performClick()
        ui.onNodeWithText("+ Học phần").performClick()
        ui.onNodeWithText("Tên").performTextInput("Meeting")
        ui.onNodeWithText("Lưu").performClick()
        ui.onNodeWithText("+ Thêm thẻ").performScrollTo().performClick()
        ui.onNodeWithText("Từ / cụm từ").performTextInput("agenda")
        ui.onNodeWithText("Nghĩa tiếng Việt").performTextInput("chương trình họp")
        ui.onNodeWithText("Lưu").performClick()
        ui.waitUntil(5000) { runBlocking { db.dao().cards().first().any { it.word == "agenda" } } }
        runBlocking {
            assertTrue(db.dao().sets().first().any { it.name == "Meeting" })
            assertTrue(db.dao().folders().first().any { it.name == "Work" })
        }
    }
}
