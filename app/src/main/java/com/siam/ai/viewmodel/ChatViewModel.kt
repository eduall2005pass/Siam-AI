package com.siam.ai.viewmodel

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siam.ai.model.ChatMessage
import com.siam.ai.model.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ChatViewModel : ViewModel() {

    // ← তোমার Azure VM IP এখানে বসাও
    private val BASE_URL = "http://4.193.107.217:3000"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _isCodingMode = MutableStateFlow(false)
    val isCodingMode: StateFlow<Boolean> = _isCodingMode

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(
        listOf(
            ChatSession(title = "What is AI?"),
            ChatSession(title = "Android development tips"),
            ChatSession(title = "Explain Kotlin coroutines")
        )
    )
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    fun onInputChange(text: String) { _inputText.value = text }

    fun toggleCodingMode() { _isCodingMode.value = !_isCodingMode.value }

    // ═══════════════════════════════
    // SEND TEXT MESSAGE
    // ═══════════════════════════════
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        val userMsg = ChatMessage(content = text, isUser = true)
        _messages.value = _messages.value + userMsg
        _inputText.value = ""

        viewModelScope.launch {
            _isTyping.value = true
            val reply = getAIResponse(text)
            _isTyping.value = false
            _messages.value = _messages.value + ChatMessage(content = reply, isUser = false)
        }
    }

    // ═══════════════════════════════
    // SEND IMAGE
    // ═══════════════════════════════
    fun sendImage(bitmap: Bitmap, prompt: String = "এই ছবিতে কী আছে বিস্তারিত বলো।") {
        val userMsg = ChatMessage(content = "📷 $prompt", isUser = true)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            _isTyping.value = true
            val reply = analyzeImage(bitmap, prompt)
            _isTyping.value = false
            _messages.value = _messages.value + ChatMessage(content = reply, isUser = false)
        }
    }

    // ═══════════════════════════════
    // API CALLS
    // ═══════════════════════════════
    private suspend fun getAIResponse(input: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Build history (last 10 messages)
                val historyArray = JSONArray()
                _messages.value.takeLast(10).forEach { msg ->
                    val obj = JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "assistant")
                        put("content", msg.content)
                    }
                    historyArray.put(obj)
                }

                val json = JSONObject().apply {
                    put("message", input)
                    put("history", historyArray)
                    put("mode", if (_isCodingMode.value) "code" else "auto")
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$BASE_URL/chat")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseJson = JSONObject(response.body!!.string())
                responseJson.getString("reply")
            } catch (e: Exception) {
                "দুঃখিত, সংযোগে সমস্যা হচ্ছে। আবার চেষ্টা করো।"
            }
        }
    }

    private suspend fun analyzeImage(bitmap: Bitmap, prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                val json = JSONObject().apply {
                    put("imageBase64", base64)
                    put("mimeType", "image/jpeg")
                    put("prompt", prompt)
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$BASE_URL/analyze-image")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                JSONObject(response.body!!.string()).getString("reply")
            } catch (e: Exception) {
                "ছবি বিশ্লেষণ করতে পারছি না, আবার চেষ্টা করো।"
            }
        }
    }

    suspend fun translate(text: String, targetLang: String = "Bengali"): String {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("text", text)
                    put("targetLang", targetLang)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$BASE_URL/translate")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                JSONObject(response.body!!.string()).getString("translated")
            } catch (e: Exception) {
                "Translation failed. Try again."
            }
        }
    }

    // ═══════════════════════════════
    // CHAT MANAGEMENT
    // ═══════════════════════════════
    fun deleteMessage(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
    }

    fun newChat() {
        if (_messages.value.isNotEmpty()) {
            val session = ChatSession(title = _messages.value.first().content.take(30))
            _chatSessions.value = listOf(session) + _chatSessions.value
        }
        _messages.value = emptyList()
    }
}
