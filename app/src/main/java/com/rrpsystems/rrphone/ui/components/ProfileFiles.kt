package com.rrpsystems.rrphone.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.rrpsystems.rrphone.core.profile.ProfileStore
import com.rrpsystems.rrphone.core.settings.AccountProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Importar um .rrpprofile (D-13): escolhe o arquivo, pergunta a senha só se o
 * arquivo exigir, e devolve o perfil já aberto. A derivação da chave leva um
 * instante (200 mil iterações), então roda fora da thread de UI.
 */
@Composable
fun rememberProfileImporter(
    onImported: (AccountProfile) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingData by remember { mutableStateOf<ByteArray?>(null) }

    fun open(data: ByteArray, passphrase: String) {
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { ProfileStore.import(data, passphrase) }
            }
            result.fold(onImported) { onError(it.message ?: "Falha ao importar.") }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val data = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (data == null) {
            onError("Não foi possível ler o arquivo.")
            return@rememberLauncherForActivityResult
        }
        val needsPassphrase = runCatching { ProfileStore.requiresPassphrase(data) }.getOrElse {
            onError(it.message ?: "Arquivo inválido.")
            return@rememberLauncherForActivityResult
        }
        if (needsPassphrase) pendingData = data else open(data, "")
    }

    pendingData?.let { data ->
        PassphraseDialog(
            title = "Senha do arquivo",
            message = "Este arquivo foi protegido com senha na exportação.",
            onConfirm = { pass -> pendingData = null; open(data, pass) },
            onDismiss = { pendingData = null },
        )
    }

    // "*/*": o Android não conhece a extensão .rrpprofile, então filtrar por
    // tipo esconderia o arquivo.
    return { picker.launch(arrayOf("*/*")) }
}

/** Exportar a configuração atual para um arquivo escolhido pelo usuário. */
@Composable
fun rememberProfileExporter(
    profile: () -> AccountProfile?,
    onDone: (String) -> Unit,
): (passphrase: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPassphrase by remember { mutableStateOf("") }

    val creator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val current = profile()
        if (uri == null || current == null) return@rememberLauncherForActivityResult
        val passphrase = pendingPassphrase
        pendingPassphrase = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ProfileStore.export(current, passphrase)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("Não foi possível gravar o arquivo.")
                }
            }
            onDone(result.fold({ "Configuração exportada com sucesso." }, { "Falha ao exportar: ${it.message}" }))
        }
    }
    return { passphrase ->
        pendingPassphrase = passphrase
        creator.launch("rrp.rrpprofile")
    }
}
