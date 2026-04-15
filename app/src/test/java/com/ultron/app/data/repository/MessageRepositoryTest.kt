package com.ultron.app.data.repository

import com.ultron.app.data.local.*
import com.ultron.app.data.remote.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MessageRepositoryTest {
    private lateinit var telegramApi: TelegramApi
    private lateinit var dao: VoiceEntryDao
    private lateinit var repo: MessageRepository

    @Before
    fun setup() {
        telegramApi = mockk()
        dao = mockk(relaxed = true)
        repo = MessageRepository(telegramApi, dao)
    }

    @Test
    fun `전송 성공 시 상태 SENT 업데이트`() = runTest {
        val entry = VoiceEntry(id = 1, transcript = "테스트")
        coEvery { telegramApi.sendMessage(any(), any()) } returns
            Response.success(TelegramResponse(ok = true, result = null))

        val result = repo.sendToTelegram(entry, "token", "chatId")

        assertTrue(result)
        coVerify { dao.updateStatus(1, SendStatus.SENT, any()) }
    }

    @Test
    fun `전송 실패 시 상태 FAILED 업데이트`() = runTest {
        val entry = VoiceEntry(id = 1, transcript = "테스트")
        coEvery { telegramApi.sendMessage(any(), any()) } throws RuntimeException("네트워크 오류")

        val result = repo.sendToTelegram(entry, "token", "chatId")

        assertFalse(result)
        coVerify { dao.updateStatus(1, SendStatus.FAILED, null) }
    }

    @Test
    fun `카테고리 힌트가 메시지에 포함됨`() = runTest {
        val entry = VoiceEntry(id = 1, transcript = "시약 투입", categoryHint = Category.ELN)
        val slot = slot<SendMessageRequest>()
        coEvery { telegramApi.sendMessage(any(), capture(slot)) } returns
            Response.success(TelegramResponse(ok = true, result = null))

        repo.sendToTelegram(entry, "token", "chatId")

        assertTrue(slot.captured.text.startsWith("[ELN]"))
    }
}
