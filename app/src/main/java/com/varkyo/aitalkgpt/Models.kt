package com.varkyo.aitalkgpt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WsBaseEvent(
    val type: String
)

@Serializable
data class InputAudioAppendEvent(
    val type: String,
    val audio: String
)

/** Common incoming event shapes (simplified) */
@Serializable
data class OutputAudioBufferAppend(
    val type: String,
    val audio: String? = null,
    val delta: String? = null
)

@Serializable
data class ConversationTranscription(
    val type: String,
    val transcript: String? = null,
    val item: JsonObject? = null
)

@Serializable
data class ErrorEvent(
    val type: String,
    val error: String? = null
)
