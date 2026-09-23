package com.rrpsystems.rrphone.core.contacts

import android.content.Context
import android.util.Base64
import android.util.Log
import android.util.Xml
import com.rrpsystems.rrphone.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Uma linha <contact> da agenda remota. Campos e nomes seguem o schema
 * <contacts>/<contact> do desktop (desktop/src/contacts/Contact.h), para que o
 * mesmo XML sirva aos dois apps sem conversão.
 */
data class Contact(
    val name: String,
    val number: String,   // o que é discado ao tocar no contato
    val phone: String = "",
    val mobile: String = "",
    val email: String = "",
    val info: String = "", // texto livre, tipicamente empresa/setor
    val local: Boolean = false,
)

/**
 * Agenda: contatos do XML remoto (D-16) mais os criados no aparelho. A lista
 * remota é guardada em cache para aparecer mesmo sem rede ao abrir o app.
 */
object ContactsRepository {
    private const val TAG = "ContactsRepository"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private lateinit var localFile: File
    private lateinit var remoteCacheFile: File

    private val _remote = MutableStateFlow<List<Contact>>(emptyList())
    val remote: StateFlow<List<Contact>> = _remote.asStateFlow()

    private val _local = MutableStateFlow<List<Contact>>(emptyList())
    val local: StateFlow<List<Contact>> = _local.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var url: String = ""
    private var autoRefreshJob: Job? = null

    fun init(context: Context) {
        if (::localFile.isInitialized) return
        localFile = File(context.filesDir, "contacts_local.json")
        remoteCacheFile = File(context.filesDir, "contacts_remote_cache.json")
        _local.value = readJson(localFile, local = true)
        _remote.value = readJson(remoteCacheFile, local = false)
    }

    /** Troca a URL da agenda remota e baixa na hora. Vazio desliga. */
    fun setUrl(newUrl: String) {
        url = newUrl.trim()
        autoRefreshJob?.cancel()
        if (url.isEmpty()) {
            _remote.value = emptyList()
            remoteCacheFile.delete()
            _status.value = ""
            return
        }
        refresh()
    }

    fun refresh() {
        if (url.isEmpty()) {
            _status.value = "Nenhuma URL de contatos configurada."
            return
        }
        scope.launch { fetchOnce() }
    }

    private suspend fun fetchOnce() {
        _refreshing.value = true
        try {
            val builder = Request.Builder().url(url)
            // Credenciais no próprio URL (http://user:pass@host/...), como o
            // desktop aceita — o OkHttp não as aplica sozinho.
            URI(url).userInfo?.takeIf { it.isNotEmpty() }?.let { userInfo ->
                builder.header("Authorization", "Basic " + Base64.encodeToString(userInfo.toByteArray(), Base64.NO_WRAP))
            }
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    _status.value = "Falha ao baixar contatos: HTTP ${response.code}"
                    return
                }
                val (contacts, refreshMinutes) = parseXml(response.body?.string() ?: "")
                _remote.value = contacts
                writeJson(remoteCacheFile, contacts)
                if (SettingsStore.replaceLocalContacts && _local.value.isNotEmpty()) {
                    saveLocal(emptyList())
                }
                _status.value = if (contacts.isEmpty()) "O servidor não retornou contatos."
                else "${contacts.size} contatos do servidor."
                scheduleAutoRefresh(refreshMinutes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar contatos", e)
            _status.value = "Falha ao baixar contatos: ${e.localizedMessage ?: e.javaClass.simpleName}"
        } finally {
            _refreshing.value = false
        }
    }

    private fun scheduleAutoRefresh(minutes: Int) {
        autoRefreshJob?.cancel()
        if (minutes <= 0) return
        autoRefreshJob = scope.launch {
            delay(minutes * 60_000L)
            fetchOnce()
        }
    }

    // --- Locais ------------------------------------------------------------

    fun addLocal(contact: Contact) = saveLocal(_local.value + contact.copy(local = true))

    fun updateLocal(old: Contact, new: Contact) =
        saveLocal(_local.value.map { if (it == old) new.copy(local = true) else it })

    fun removeLocal(contact: Contact) = saveLocal(_local.value - contact)

    private fun saveLocal(contacts: List<Contact>) {
        _local.value = contacts
        writeJson(localFile, contacts)
    }

    // --- Parsing / persistência ---------------------------------------------

    /** Tolerante: atributos ausentes viram string vazia; nomes sem distinção de caixa. */
    private fun parseXml(xml: String): Pair<List<Contact>, Int> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val contacts = mutableListOf<Contact>()
        var refresh = 0
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val attrs = (0 until parser.attributeCount).associate {
                parser.getAttributeName(it).lowercase() to (parser.getAttributeValue(it) ?: "")
            }
            when (parser.name.lowercase()) {
                "contacts" -> refresh = attrs["refresh"]?.toIntOrNull() ?: 0
                "contact" -> {
                    val number = attrs["number"].orEmpty().trim()
                    val name = attrs["name"].orEmpty().trim().ifEmpty {
                        listOf(attrs["firstname"].orEmpty(), attrs["lastname"].orEmpty()).joinToString(" ").trim()
                    }
                    if (number.isNotEmpty() || name.isNotEmpty()) {
                        contacts += Contact(
                            name = name.ifEmpty { number },
                            number = number,
                            phone = attrs["phone"].orEmpty(),
                            mobile = attrs["mobile"].orEmpty(),
                            email = attrs["email"].orEmpty(),
                            info = attrs["info"].orEmpty(),
                        )
                    }
                }
            }
        }
        return contacts to refresh
    }

    private fun readJson(file: File, local: Boolean): List<Contact> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Contact(
                    name = o.optString("name"),
                    number = o.optString("number"),
                    phone = o.optString("phone"),
                    mobile = o.optString("mobile"),
                    email = o.optString("email"),
                    info = o.optString("info"),
                    local = local,
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeJson(file: File, contacts: List<Contact>) {
        val arr = JSONArray()
        contacts.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("number", it.number).put("phone", it.phone)
                    .put("mobile", it.mobile).put("email", it.email).put("info", it.info)
            )
        }
        file.writeText(arr.toString())
    }
}
