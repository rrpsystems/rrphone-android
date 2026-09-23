package com.rrpsystems.rrphone.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrpsystems.rrphone.BuildConfig
import com.rrpsystems.rrphone.core.contacts.ContactsRepository
import com.rrpsystems.rrphone.core.settings.AccountProfile
import com.rrpsystems.rrphone.core.settings.CodecInfo
import com.rrpsystems.rrphone.core.settings.SettingsStore
import com.rrpsystems.rrphone.core.sip.CallManager
import com.rrpsystems.rrphone.core.sip.LinphoneManager
import com.rrpsystems.rrphone.core.sip.Registration
import com.rrpsystems.rrphone.ui.components.PassphraseDialog
import com.rrpsystems.rrphone.ui.components.Routes
import com.rrpsystems.rrphone.ui.components.TabScaffold
import com.rrpsystems.rrphone.ui.components.rememberProfileExporter
import com.rrpsystems.rrphone.ui.components.rememberProfileImporter
import com.rrpsystems.rrphone.ui.theme.Rrp

/**
 * Ajustes — o equivalente mobile da janela de Configurações do desktop:
 * conta (D-01), chamadas (não perturbe e siga-me, que no celular ficam aqui
 * para não serem acionados sem querer), áudio, codecs (D-14), DTMF (D-15),
 * agenda (D-16) e importação/exportação do perfil (D-13).
 */
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onReturnToCall: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val saved = remember { SettingsStore.loadProfile() ?: AccountProfile() }
    var profile by remember { mutableStateOf(saved) }
    var message by remember { mutableStateOf<String?>(null) }

    fun persist(updated: AccountProfile) {
        profile = updated
        SettingsStore.saveProfile(updated)
    }

    val importer = rememberProfileImporter(
        onImported = { imported ->
            persist(imported)
            LinphoneManager.applyAccount(imported)
            ContactsRepository.setUrl(imported.contactsUrl)
            message = "Configuração importada e aplicada."
        },
        onError = { message = it },
    )
    val exporter = rememberProfileExporter(
        profile = { SettingsStore.loadProfile() },
        onDone = { message = it },
    )

    TabScaffold(currentRoute = Routes.SETTINGS, onNavigate = onNavigate, onReturnToCall = onReturnToCall) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountSection(profile) { updated ->
                persist(updated)
                LinphoneManager.applyAccount(updated)
                message = "Conta salva. Registrando novamente..."
            }
            CallsSection()
            AudioSection(profile) { persist(it) }
            ContactsSection(profile) { updated ->
                persist(updated)
                ContactsRepository.setUrl(updated.contactsUrl)
            }
            ProfileSection(onImport = importer, onExport = exporter)
            AboutSection()
            LogoutSection(onLoggedOut)
            Spacer(Modifier.height(8.dp))
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = Rrp.Panel,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

// --- Blocos ----------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Rrp.Panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Rrp.AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = Rrp.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Rrp.TextPrimary, fontSize = 15.sp)
            if (subtitle != null) Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccountSection(profile: AccountProfile, onSave: (AccountProfile) -> Unit) {
    val registration by LinphoneManager.registration.collectAsState()
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var username by remember(profile) { mutableStateOf(profile.username) }
    var password by remember(profile) { mutableStateOf(profile.password) }
    var domain by remember(profile) { mutableStateOf(profile.domain) }
    var transport by remember(profile) { mutableStateOf(profile.transport) }
    var pushEnabled by remember(profile) { mutableStateOf(profile.pushEnabled) }
    var outboundProxy by remember(profile) { mutableStateOf(profile.outboundProxy) }
    var showPassword by remember { mutableStateOf(false) }

    Section("Conta") {
        Text(
            when (val r = registration) {
                Registration.Ok -> "Registrado"
                Registration.Progress -> "Registrando..."
                is Registration.Failed -> "Falha no registro: ${r.message}"
                Registration.None -> "Não registrado"
            },
            color = when (registration) {
                Registration.Ok -> Rrp.Green
                is Registration.Failed -> Rrp.Red
                else -> Rrp.TextSecondary
            },
            fontSize = 13.sp
        )
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Nome de exibição") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("Usuário/Ramal") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it }, label = { Text("Senha") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Mostrar senha")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(domain, { domain = it }, label = { Text("Servidor SIP") },
            placeholder = { Text("sip.exemplo.com[:porta]") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        TransportSelector(transport) { transport = it }
        PushProxyFields(
            pushEnabled = pushEnabled, onPushChange = { pushEnabled = it },
            proxy = outboundProxy, onProxyChange = { outboundProxy = it },
        )
        val dirty = displayName != profile.displayName || username != profile.username ||
            password != profile.password || domain != profile.domain || transport != profile.transport ||
            outboundProxy.trim() != profile.outboundProxy || pushEnabled != profile.pushEnabled
        Button(
            onClick = {
                onSave(
                    profile.copy(
                        displayName = displayName.trim(), username = username.trim(), password = password,
                        domain = domain.trim(), transport = transport, pushEnabled = pushEnabled,
                        outboundProxy = outboundProxy.trim()
                    )
                )
            },
            enabled = username.isNotBlank() && domain.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (dirty) "Salvar e registrar" else "Registrar novamente") }
    }
}

/**
 * Push: uma chave, não um endereço. O único servidor que acorda este app é o
 * Flexisip da RRP (o push sai do nosso projeto Firebase), então não há o que
 * escolher. Desligado, registra direto no servidor SIP; o campo de proxy só
 * aparece aí, para o caso raro de um SBC/proxy do cliente.
 */
@Composable
fun PushProxyFields(
    pushEnabled: Boolean,
    onPushChange: (Boolean) -> Unit,
    proxy: String,
    onProxyChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable { onPushChange(!pushEnabled) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Receber chamadas com o app fechado", color = Rrp.TextPrimary, fontSize = 15.sp)
            Text(
                if (pushEnabled) "Push pelo servidor RRP. Recomendado."
                else "Desligado: o app só recebe chamadas enquanto está aberto.",
                color = Rrp.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        Switch(checked = pushEnabled, onCheckedChange = onPushChange)
    }
    if (!pushEnabled) {
        OutlinedTextField(
            proxy, onProxyChange, label = { Text("Proxy de saída (opcional)") },
            placeholder = { Text("vazio = direto no servidor") },
            supportingText = { Text("Só se a rede do cliente exigir um proxy/SBC. Sem porta, usa TLS 5061.") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TransportSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("udp" to "UDP", "tcp" to "TCP", "tls" to "TLS")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected.equals(value, ignoreCase = true),
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun CallsSection() {
    val dnd by CallManager.doNotDisturb.collectAsState()
    val forward by CallManager.forwardTarget.collectAsState()
    var forwardInput by remember(forward) { mutableStateOf(forward) }

    Section("Chamadas") {
        SwitchRow(
            "Não perturbe",
            "Chamadas recebidas são recusadas como ocupado e ficam no histórico.",
            dnd
        ) { CallManager.setDoNotDisturb(it) }
        HorizontalDivider(color = Rrp.Border)
        Text("Siga-me", color = Rrp.TextPrimary, fontSize = 15.sp)
        Hint("Toda chamada recebida vai direto para o número abaixo, sem tocar neste aparelho. Tem prioridade sobre o não perturbe.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                forwardInput, { forwardInput = it }, label = { Text("Encaminhar para") },
                placeholder = { Text("ex.: 2130") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
            if (forward.isEmpty()) {
                Button(onClick = { CallManager.setForwardTarget(forwardInput) }, enabled = forwardInput.isNotBlank()) {
                    Text("Ativar")
                }
            } else {
                Button(
                    onClick = { CallManager.setForwardTarget(""); forwardInput = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = Rrp.Red)
                ) { Text("Desativar") }
            }
        }
        if (forward.isNotEmpty()) {
            Text("Ativo: chamadas indo para $forward", color = Rrp.AccentBlue, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AudioSection(profile: AccountProfile, onProfileChange: (AccountProfile) -> Unit) {
    var noise by remember { mutableStateOf(SettingsStore.noiseSuppression) }
    var echo by remember { mutableStateOf(SettingsStore.echoCancellation) }
    var agc by remember { mutableStateOf(SettingsStore.automaticGainControl) }
    var codecs by remember { mutableStateOf(LinphoneManager.audioCodecs()) }

    fun applyCodecs(updated: List<CodecInfo>) {
        codecs = updated
        LinphoneManager.setAudioCodecsOrder(updated)
        onProfileChange(profile.copy(codecs = updated.filter { it.enabled }.map { it.mimeType }))
    }

    Section("Áudio") {
        Hint("Processamento do microfone — vale a partir da próxima chamada.")
        SwitchRow(
            "Supressão de ruído",
            "Reduz ruído de fundo no que você envia, mas tira parte da naturalidade da voz. Deixe desligada em ambiente silencioso.",
            noise
        ) {
            noise = it
            SettingsStore.noiseSuppression = it
            LinphoneManager.setAudioProcessing(noise, echo, agc)
        }
        SwitchRow("Cancelamento de eco", "Evita que o outro lado ouça a própria voz de volta.", echo) {
            echo = it
            SettingsStore.echoCancellation = it
            LinphoneManager.setAudioProcessing(noise, echo, agc)
        }
        SwitchRow(
            "Controle automático de ganho",
            "Nivela o volume da sua voz; também realça o ruído de fundo nas pausas.", agc
        ) {
            agc = it
            SettingsStore.automaticGainControl = it
            LinphoneManager.setAudioProcessing(noise, echo, agc)
        }
        HorizontalDivider(color = Rrp.Border)
        Text("Codecs (prioridade e habilitação)", color = Rrp.TextPrimary, fontSize = 15.sp)
        codecs.forEachIndexed { index, codec ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${codec.mimeType.uppercase()}  ${codec.clockRate / 1000} kHz",
                    color = if (codec.enabled) Rrp.TextPrimary else Rrp.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    applyCodecs(codecs.toMutableList().apply { add(index - 1, removeAt(index)) })
                }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
                IconButton(onClick = {
                    applyCodecs(codecs.toMutableList().apply { add(index + 1, removeAt(index)) })
                }, enabled = index < codecs.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Descer") }
                Switch(checked = codec.enabled, onCheckedChange = { on ->
                    // Pelo menos um codec tem de ficar: sem nenhum, nenhuma chamada negocia.
                    if (on || codecs.count { it.enabled } > 1) {
                        applyCodecs(codecs.map { if (it == codec) it.copy(enabled = on) else it })
                    }
                })
            }
        }
        HorizontalDivider(color = Rrp.Border)
        Text("Método de DTMF", color = Rrp.TextPrimary, fontSize = 15.sp)
        val methods = listOf(
            "rfc2833" to "RFC2833 (fora de banda, recomendado)",
            "info" to "SIP INFO",
            "inband" to "In-band (tons no áudio)",
        )
        methods.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    LinphoneManager.setDtmfMethod(value)
                    onProfileChange(profile.copy(dtmfMethod = value))
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = profile.dtmfMethod == value, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(label, color = Rrp.TextPrimary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ContactsSection(profile: AccountProfile, onSave: (AccountProfile) -> Unit) {
    var url by remember(profile.contactsUrl) { mutableStateOf(profile.contactsUrl) }
    var replaceLocal by remember { mutableStateOf(SettingsStore.replaceLocalContacts) }
    Section("Agenda") {
        OutlinedTextField(
            url, { url = it }, label = { Text("URL da lista de contatos") },
            placeholder = { Text("https://.../contacts.xml") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
        )
        Hint("Formato <contacts>/<contact>, o mesmo do desktop. Deixe em branco para não usar.")
        SwitchRow(
            "Apagar os contatos locais quando a lista do servidor chegar",
            "Use quando a agenda do servidor deve ser a única fonte.", replaceLocal
        ) {
            replaceLocal = it
            SettingsStore.replaceLocalContacts = it
        }
        Button(
            onClick = { onSave(profile.copy(contactsUrl = url.trim())) },
            enabled = url.trim() != profile.contactsUrl,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Salvar e baixar") }
    }
}

@Composable
private fun ProfileSection(onImport: () -> Unit, onExport: (String) -> Unit) {
    var protect by remember { mutableStateOf(false) }
    var askPassphrase by remember { mutableStateOf(false) }
    Section("Configuração") {
        Hint("Um arquivo .rrpprofile leva a conta, codecs, DTMF e a agenda para outro aparelho ou para o desktop.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Importar...") }
            OutlinedButton(
                onClick = { if (protect) askPassphrase = true else onExport("") },
                modifier = Modifier.weight(1f)
            ) { Text("Exportar...") }
        }
        SwitchRow(
            "Proteger a exportação com uma senha",
            "Sem senha, o arquivo é cifrado com a chave do próprio app: protege contra olhares, não contra quem tem o app.",
            protect
        ) { protect = it }
    }
    if (askPassphrase) {
        PassphraseDialog(
            title = "Senha do arquivo",
            message = "Quem for importar vai precisar desta senha. Envie-a por outro canal, não junto com o arquivo.",
            onConfirm = { askPassphrase = false; onExport(it) },
            onDismiss = { askPassphrase = false },
        )
    }
}

@Composable
private fun AboutSection() {
    Section("Sobre") {
        Text("RRP Softphone ${BuildConfig.VERSION_NAME}", color = Rrp.TextPrimary)
        Hint("© 2026 RRP Systems Ltda.\nMotor SIP: liblinphone (Belledonne Communications), GNU GPL v3.")
    }
}

@Composable
private fun LogoutSection(onLoggedOut: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rrp.Red)
    ) { Text("Sair da conta") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Rrp.Panel,
            title = { Text("Sair da conta") },
            text = { Text("O ramal deixa de receber chamadas neste aparelho até ser configurado de novo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    CallManager.hangUp()
                    LinphoneManager.clearAccount()
                    SettingsStore.clearProfile()
                    ContactsRepository.setUrl("")
                    onLoggedOut()
                }) { Text("Sair", color = Rrp.Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
