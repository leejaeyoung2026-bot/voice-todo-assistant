package com.ultron.app.data.local

import org.junit.Assert.*
import org.junit.Test

class VoiceEntryTest {
    @Test
    fun `새 VoiceEntry 기본 상태는 PENDING`() {
        val entry = VoiceEntry(transcript = "내일 회의 잡아줘")
        assertEquals(SendStatus.PENDING, entry.sendStatus)
        assertNull(entry.categoryHint)
        assertNull(entry.sentAt)
        assertEquals(0, entry.retryCount)
    }

    @Test
    fun `카테고리 힌트 지정 가능`() {
        val entry = VoiceEntry(
            transcript = "시약 A 10ml 투입",
            categoryHint = Category.ELN
        )
        assertEquals(Category.ELN, entry.categoryHint)
    }

    @Test
    fun `SendStatus enum 변환`() {
        val converter = Converters()
        assertEquals("PENDING", converter.fromSendStatus(SendStatus.PENDING))
        assertEquals(SendStatus.SENT, converter.toSendStatus("SENT"))
    }

    @Test
    fun `Category nullable 변환`() {
        val converter = Converters()
        assertNull(converter.fromCategory(null))
        assertNull(converter.toCategory(null))
        assertEquals("TODO", converter.fromCategory(Category.TODO))
        assertEquals(Category.TODO, converter.toCategory("TODO"))
    }
}
