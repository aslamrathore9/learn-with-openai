package com.varkyo.aitalkgpt

import kotlinx.serialization.Serializable


@Serializable
data class WsEvent(
    val type: String,
    val audio: String? = null,
    val delta: String? = null,
    val id: String? = null,
    val response: String? = null
)


// For UI display
data class ChatLine(val role: String, val text: String)