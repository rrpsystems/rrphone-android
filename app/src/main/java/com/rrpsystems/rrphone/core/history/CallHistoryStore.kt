package com.rrpsystems.rrphone.core.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Uma entrada do histórico — mesmos campos do desktop (CallHistoryStore.h). */
data class CallRecord(
    val peer: String,              // ramal/número; é o que o "ligar de novo" usa
    val displayName: String = "",
    val startedAt: Long,           // epoch millis
    val durationSeconds: Int = 0,
    val incoming: Boolean,
    val answered: Boolean,
    // Por que terminou assim, quando não foi simplesmente atendida:
    // "recusada (não perturbe)", "encaminhada para 2130", "não atendida".
    val note: String = "",
)

/**
 * Histórico em JSON no armazenamento privado do app. O liblinphone só guarda
 * call logs em memória sem banco de dados, e o core é criado sem arquivos de
 * configuração — mesma decisão do desktop.
 */
object CallHistoryStore {
    // Uma instalação pode rodar por anos; entradas antigas caem daqui para frente.
    private const val MAX_RECORDS = 300

    private lateinit var file: File
    private val _records = MutableStateFlow<List<CallRecord>>(emptyList())
    val records: StateFlow<List<CallRecord>> = _records.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "call_history.json")
        _records.value = load()
    }

    /** Número da última chamada efetuada, para o gesto de rediscar. */
    fun lastDialedNumber(): String =
        _records.value.firstOrNull { !it.incoming && it.peer.isNotBlank() }?.peer ?: ""

    @Synchronized
    fun append(record: CallRecord) {
        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        save(updated)
    }

    @Synchronized
    fun clear() {
        _records.value = emptyList()
        file.delete()
    }

    private fun load(): List<CallRecord> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CallRecord(
                    peer = o.optString("peer"),
                    displayName = o.optString("displayName"),
                    startedAt = o.optLong("startedAt"),
                    durationSeconds = o.optInt("durationSeconds"),
                    incoming = o.optBoolean("incoming"),
                    answered = o.optBoolean("answered"),
                    note = o.optString("note"),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(records: List<CallRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                JSONObject()
                    .put("peer", r.peer)
                    .put("displayName", r.displayName)
                    .put("startedAt", r.startedAt)
                    .put("durationSeconds", r.durationSeconds)
                    .put("incoming", r.incoming)
                    .put("answered", r.answered)
                    .put("note", r.note)
            )
        }
        file.writeText(arr.toString())
    }
}
