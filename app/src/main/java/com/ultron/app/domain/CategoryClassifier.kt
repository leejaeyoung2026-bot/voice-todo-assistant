package com.ultron.app.domain

import com.ultron.app.data.local.Category

data class ClassificationResult(
    val category: Category,
    val confidence: Float
)

class CategoryClassifier {
    private data class Pattern(val keywords: List<String>, val category: Category, val weight: Float)

    private val patterns = listOf(
        Pattern(listOf("미팅", "회의", "약속", "예약", "시에", "시부터"), Category.SCHEDULE, 0.8f),
        Pattern(listOf("실험", "배치", "시약", "반응", "수율", "순도", "샘플", "분석"), Category.ELN, 0.8f),
        Pattern(listOf("해야", "해줘", "잊지 마", "사야", "할 일", "처리", "확인해"), Category.TODO, 0.6f),
        Pattern(listOf("어떻게 생각", "조언", "알려줘", "뭐야", "왜"), Category.CHAT, 0.3f),
    )

    private val dateTimeRegex = Regex(
        "(내일|모레|다음 ?주|오늘|월요일|화요일|수요일|목요일|금요일|토요일|일요일|" +
        "\\d{1,2}월|\\d{1,2}일|\\d{1,2}시|오전|오후)"
    )

    fun classify(text: String): ClassificationResult {
        val scores = mutableMapOf<Category, Float>()

        for (pattern in patterns) {
            val matchCount = pattern.keywords.count { text.contains(it) }
            if (matchCount > 0) {
                val score = pattern.weight * (matchCount.toFloat() / pattern.keywords.size + 0.5f)
                scores[pattern.category] = (scores[pattern.category] ?: 0f) + score
            }
        }

        if (dateTimeRegex.containsMatchIn(text)) {
            scores[Category.SCHEDULE] = (scores[Category.SCHEDULE] ?: 0f) + 0.5f
        }

        val best = scores.maxByOrNull { it.value }
        return if (best != null && best.value > 0.3f) {
            ClassificationResult(best.key, best.value.coerceAtMost(1.0f))
        } else {
            ClassificationResult(Category.CHAT, 0.2f)
        }
    }
}
