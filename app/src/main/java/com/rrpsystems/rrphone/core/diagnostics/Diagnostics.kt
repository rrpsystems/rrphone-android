package com.rrpsystems.rrphone.core.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rrpsystems.rrphone.BuildConfig
import com.rrpsystems.rrphone.core.settings.SettingsStore
import com.rrpsystems.rrphone.core.settings.sipServerHostPort
import com.rrpsystems.rrphone.core.sip.LinphoneManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.LogLevel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Diagnóstico para suporte: o usuário liga o registro detalhado, reproduz o
 * problema e envia um .zip pelo app que escolher (e-mail, WhatsApp...). Nada
 * sai do aparelho sem esse toque em "Enviar".
 *
 * O pacote leva:
 * - info.txt: versão, aparelho, Android, conta (sem a senha), permissões;
 * - linphone/: o log do liblinphone em arquivo (SIP, registro, áudio), que
 *   sobrevive ao app ser fechado e reaberto — só existe com o registro ligado;
 * - logcat.txt: o log do app (CallManager, push...) desde que o processo
 *   atual subiu;
 * - falhas/: o rastro de cada travamento do app, gravado sempre (é pequeno e
 *   é justamente o que se perde quando o app fecha sozinho);
 * - encerramentos.txt: como o processo terminou nas últimas vezes, segundo o
 *   Android (inclui "não está respondendo" e travamentos nativos).
 */
object Diagnostics {
    private const val TAG = "Diagnostics"
    private const val DAYS_ON = 7
    const val SUPPORT_EMAIL = "suporte@rrpsystems.com.br"

    private lateinit var app: Context
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val logsDir get() = File(app.filesDir, "diagnostico/linphone")
    private val crashDir get() = File(app.filesDir, "diagnostico/falhas")
    private val outDir get() = File(app.cacheDir, "diagnostico")

    /** Na criação do app, antes do Core: grava travamentos e desliga o registro vencido. */
    fun init(context: Context) {
        app = context.applicationContext
        val until = SettingsStore.diagnosticsUntil
        if (until != 0L && until < System.currentTimeMillis()) SettingsStore.diagnosticsUntil = 0L
        _enabled.value = SettingsStore.diagnosticsUntil != 0L

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashDir.mkdirs()
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                File(crashDir, "falha-${stamp()}.txt").writeText(
                    "RRP Softphone ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Thread: ${thread.name}\n${deviceLine()}\n\n$trace"
                )
                // Só as 10 mais recentes.
                crashDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Configura a fábrica do liblinphone; chamar antes de criar o Core. */
    fun configureFactory(factory: Factory) {
        logsDir.mkdirs()
        factory.setLogCollectionPath(logsDir.absolutePath)
        applyToFactory(factory)
    }

    private fun applyToFactory(factory: Factory) {
        val collect = _enabled.value
        factory.enableLogCollection(if (collect) LogCollectionState.Enabled else LogCollectionState.Disabled)
        // Logcat detalhado só no build de desenvolvimento.
        factory.setDebugMode(BuildConfig.DEBUG, "RRP-Linphone")
        factory.loggingService.setLogLevel(if (collect || BuildConfig.DEBUG) LogLevel.Message else LogLevel.Warning)
    }

    fun setEnabled(on: Boolean) {
        SettingsStore.diagnosticsUntil = if (on) System.currentTimeMillis() + DAYS_ON * 24 * 3600_000L else 0L
        _enabled.value = on
        applyToFactory(Factory.instance())
        Log.i(TAG, if (on) "Registro detalhado ligado por $DAYS_ON dias" else "Registro detalhado desligado")
        // O que já foi gravado antes de ligar não existe: registrar de novo
        // deixa o começo do log com a conta, o servidor e o REGISTER.
        if (on) LinphoneManager.coreOrNull()?.refreshRegisters()
    }

    /** Até quando fica ligado, para a tela mostrar. */
    fun enabledUntil(): Long = SettingsStore.diagnosticsUntil

    fun hasContent(): Boolean =
        _enabled.value || (logsDir.listFiles()?.isNotEmpty() == true) || (crashDir.listFiles()?.isNotEmpty() == true)

    fun crashCount(): Int = crashDir.listFiles()?.size ?: 0

    /** Apaga logs e falhas gravados (o registro, se ligado, continua). */
    fun clear() {
        logsDir.listFiles()?.forEach { it.delete() }
        crashDir.listFiles()?.forEach { it.delete() }
        outDir.listFiles()?.forEach { it.delete() }
        LinphoneManager.coreOrNull()?.resetLogCollection()
    }

    /** Monta o .zip e abre a escolha de app para enviar. Roda fora da thread principal. */
    fun buildReport(): File {
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }
        val zip = File(outDir, "rrp-diagnostico-${stamp()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            fun add(name: String, bytes: ByteArray) {
                out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry()
            }
            add("info.txt", info().toByteArray())
            add("logcat.txt", ownLogcat())
            add("encerramentos.txt", exitReasons())
            logsDir.listFiles()?.forEach { add("linphone/${it.name}", it.readBytes()) }
            crashDir.listFiles()?.forEach { add("falhas/${it.name}", it.readBytes()) }
        }
        return zip
    }

    fun shareIntent(zip: File): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.diagnostico", zip)
        val profile = SettingsStore.loadProfile()
        val who = profile?.let { "${it.username}@${it.domain}" } ?: "sem conta"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico RRP Softphone ${BuildConfig.VERSION_NAME} — $who")
            putExtra(Intent.EXTRA_TEXT,
                "Diagnóstico do RRP Softphone ${BuildConfig.VERSION_NAME}.\n${deviceLine()}\nConta: $who\n\n" +
                    "Descreva aqui o que aconteceu e por volta de que horas:\n")
            // Também como ClipData: é assim que a permissão de leitura chega à
            // prévia da tela de escolha de app (sem isso ela tenta ler e falha).
            clipData = android.content.ClipData.newRawUri(zip.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Enviar diagnóstico")
    }

    // --- Conteúdo -----------------------------------------------------------------

    private fun info(): String {
        val p = SettingsStore.loadProfile()
        val nm = app.getSystemService(NotificationManager::class.java)
        val pm = app.getSystemService(PowerManager::class.java)
        fun perm(name: String) =
            if (ContextCompat.checkSelfPermission(app, name) == PackageManager.PERMISSION_GRANTED) "sim" else "não"
        return buildString {
            appendLine("RRP Softphone ${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Gerado em: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine(deviceLine())
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            if (p == null) appendLine("Conta: nenhuma configurada") else {
                appendLine("Conta: ${p.username}@${p.domain} (servidor ${sipServerHostPort(p.domain, p.transport)}, ${p.transport})")
                appendLine("Push: ${if (p.pushEnabled) "ligado" else "desligado"} | proxy efetivo: ${p.effectiveProxy.ifEmpty { "nenhum" }}")
                appendLine("DTMF: ${p.dtmfMethod} | codecs: ${p.codecs.ifEmpty { listOf("padrão") }.joinToString()}")
            }
            appendLine("Registro: " + when (val r = LinphoneManager.registration.value) {
                com.rrpsystems.rrphone.core.sip.Registration.Ok -> "registrado"
                com.rrpsystems.rrphone.core.sip.Registration.Progress -> "registrando"
                com.rrpsystems.rrphone.core.sip.Registration.None -> "não registrado"
                is com.rrpsystems.rrphone.core.sip.Registration.Failed -> "falhou (${r.message})"
            })
            appendLine("Não perturbe: ${SettingsStore.doNotDisturb} | siga-me: ${SettingsStore.forwardTarget.ifEmpty { "não" }}")
            appendLine("Áudio: eco ${SettingsStore.echoCancellation}, ruído ${SettingsStore.noiseSuppression}, ganho ${SettingsStore.automaticGainControl}")
            appendLine("Jitter buffer: ${SettingsStore.jitterBufferMs} ms")
            appendLine()
            appendLine("Permissões: microfone ${perm(Manifest.permission.RECORD_AUDIO)}" +
                (if (Build.VERSION.SDK_INT >= 33) ", notificações ${perm(Manifest.permission.POST_NOTIFICATIONS)}" else "") +
                (if (Build.VERSION.SDK_INT >= 31) ", Bluetooth ${perm(Manifest.permission.BLUETOOTH_CONNECT)}" else "") +
                (if (Build.VERSION.SDK_INT >= 34) ", tela cheia ${if (nm.canUseFullScreenIntent()) "sim" else "não"}" else ""))
            appendLine("Notificações ativas: ${nm.areNotificationsEnabled()} | sem otimização de bateria: ${pm.isIgnoringBatteryOptimizations(app.packageName)}")
            appendLine("Registro detalhado: " + if (_enabled.value) {
                "ligado até " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(SettingsStore.diagnosticsUntil))
            } else "desligado")
        }
    }

    /**
     * Como o app terminou nas últimas vezes, segundo o próprio Android: inclui
     * o que o tratador de travamentos não pega — "não está respondendo" (ANR,
     * com o rastro das threads), travamento nativo, falta de memória, morto
     * pelo sistema.
     */
    private fun exitReasons(): ByteArray {
        if (Build.VERSION.SDK_INT < 30) return "Disponível a partir do Android 11.".toByteArray()
        return try {
            val am = app.getSystemService(android.app.ActivityManager::class.java)
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            buildString {
                am.getHistoricalProcessExitReasons(app.packageName, 0, 15).forEach { e ->
                    appendLine("${fmt.format(Date(e.timestamp))} | ${exitReason(e.reason)} | ${e.description ?: ""}")
                    if (e.reason == android.app.ApplicationExitInfo.REASON_ANR ||
                        e.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) {
                        e.traceInputStream?.use { appendLine(it.readBytes().decodeToString().take(60_000)) }
                    }
                }
            }.ifEmpty { "Nenhum encerramento registrado." }.toByteArray()
        } catch (e: Exception) {
            "Não foi possível ler: $e".toByteArray()
        }
    }

    private fun exitReason(code: Int) = when (code) {
        android.app.ApplicationExitInfo.REASON_ANR -> "não respondia (ANR)"
        android.app.ApplicationExitInfo.REASON_CRASH -> "travamento (Java)"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "travamento nativo"
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "falta de memória"
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "fechado pelo usuário"
        android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "forçar parada"
        android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "encerrou sozinho"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "encerrado por sinal"
        android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permissão alterada"
        android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependência encerrou"
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excessivo de recursos"
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "falha ao iniciar"
        android.app.ApplicationExitInfo.REASON_OTHER -> "outro (sistema)"
        else -> "código $code"
    }

    /** O log do próprio processo; o Android deixa um app ler só o que é dele. */
    private fun ownLogcat(): ByteArray = try {
        val proc = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true).start()
        proc.inputStream.readBytes().also { proc.waitFor() }
    } catch (e: Exception) {
        "Não foi possível ler o logcat: $e".toByteArray()
    }

    private fun deviceLine() =
        "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
