package com.ultron.app.data.remote

data class TelegramResponse(
    val ok: Boolean,
    val result: TelegramMessage?
)

data class TelegramMessage(
    val message_id: Int,
    val text: String?
)

data class SendMessageRequest(
    val chat_id: String,
    val text: String,
    val parse_mode: String = "Markdown"
)
