package com.ultron.app.domain

import com.ultron.app.data.local.Category
import org.junit.Assert.*
import org.junit.Test

class CategoryClassifierTest {
    private val classifier = CategoryClassifier()

    @Test
    fun `일정 키워드 감지 - 미팅`() {
        val result = classifier.classify("내일 3시에 팀 미팅 잡아줘")
        assertEquals(Category.SCHEDULE, result.category)
        assertTrue(result.confidence > 0.7)
    }

    @Test
    fun `할일 키워드 감지 - 해줘`() {
        val result = classifier.classify("보고서 초안 작성해줘")
        assertEquals(Category.TODO, result.category)
        assertTrue(result.confidence > 0.5)
    }

    @Test
    fun `실험로그 키워드 감지 - 시약`() {
        val result = classifier.classify("시약 A 10ml 투입 완료, 반응 온도 37도")
        assertEquals(Category.ELN, result.category)
        assertTrue(result.confidence > 0.7)
    }

    @Test
    fun `패턴 없으면 CHAT`() {
        val result = classifier.classify("오늘 날씨 어떻게 생각해?")
        assertEquals(Category.CHAT, result.category)
    }

    @Test
    fun `날짜 패턴이 일정 점수 부스트`() {
        val result = classifier.classify("내일 뭔가 해야 해")
        assertEquals(Category.SCHEDULE, result.category)
    }
}
