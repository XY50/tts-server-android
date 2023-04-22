@file:Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")

package com.github.jing332.tts_server_android.service.forwarder.system

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.jing332.tts_server_android.App
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.constant.AppConst
import com.github.jing332.tts_server_android.constant.KeyConst
import com.github.jing332.tts_server_android.constant.SystemNotificationConst
import com.github.jing332.tts_server_android.help.LocalTtsEngineHelper
import com.github.jing332.tts_server_android.help.config.SysTtsForwarderConfig
import com.github.jing332.tts_server_android.model.speech.tts.LocalTTS
import com.github.jing332.tts_server_android.service.forwarder.AbsForwarderService
import com.github.jing332.tts_server_android.ui.AppLog
import com.github.jing332.tts_server_android.ui.LogLevel
import com.github.jing332.tts_server_android.ui.MainActivity
import com.github.jing332.tts_server_android.ui.MainActivity.Companion.INDEX_FORWARDER_SYS
import com.github.jing332.tts_server_android.ui.MainActivity.Companion.KEY_FRAGMENT_INDEX
import com.github.jing332.tts_server_android.utils.ClipboardUtils
import com.github.jing332.tts_server_android.utils.longToast
import com.github.jing332.tts_server_android.utils.toast
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import tts_server_lib.SysTtsForwarder
import tts_server_lib.Tts_server_lib

class SysTtsForwarderService(
    override val port: Int = SysTtsForwarderConfig.port,
    override val isWakeLockEnabled: Boolean = SysTtsForwarderConfig.isWakeLockEnabled
) :
    AbsForwarderService(
        "SysTtsForwarderService",
        id = 1221,
        actionLog = ACTION_ON_LOG,
        actionStarting = ACTION_ON_STARTING,
        actionClosed = ACTION_ON_CLOSED,
        notificationChanId = "systts_forwarder_status",
        notificationChanTitle = R.string.forwarder_systts,
        notificationIcon = R.drawable.ic_baseline_compare_arrows_24,
        notificationTitle = R.string.forwarder_systts,
    ) {
    companion object {
        const val TAG = "SysTtsServerService"
        const val ACTION_ON_CLOSED = "ACTION_ON_CLOSED"
        const val ACTION_ON_STARTING = "ACTION_ON_STARTING"
        const val ACTION_ON_LOG = "ACTION_ON_LOG"

        val isRunning: Boolean
            get() = instance?.isRunning == true

        var instance: SysTtsForwarderService? = null
    }

    private var mServer: SysTtsForwarder? = null
    private var mLocalTTS: LocalTTS? = null
    private val mLocalTtsHelper by lazy { LocalTtsEngineHelper(this) }

    private val mCfg = SysTtsForwarderConfig

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun initServer() {
        mServer = SysTtsForwarder().apply {
            initCallback(object : tts_server_lib.SysTtsForwarderCallback {
                override fun log(level: Int, msg: String) {
                    sendLog(level, msg)
                }

                override fun cancelAudio(engine: String) {
                    if (mLocalTTS?.engine == engine) {
                        mLocalTTS?.onStop()
                        sendLog(LogLevel.WARN, "Canceled: $engine")
                    }
                }

                override fun getAudio(engine: String, text: String, rate: Int): String {
                    if (mLocalTTS?.engine != engine) {
                        mLocalTTS?.onDestroy()
                        mLocalTTS = LocalTTS(engine)
                    }

                    mLocalTTS?.let {
                        val file = it.getAudioFile(text, rate)
                        if (file.exists()) return file.absolutePath
                    }
                    throw Exception(getString(R.string.forwarder_sys_fail_audio_file))
                }

                override fun getEngines(): String {
                    val data = getSysTtsEngines().map { EngineInfo(it.name, it.label) }
                    return AppConst.jsonBuilder.encodeToString(data)
                }

                override fun getVoices(engine: String): String {
                    return runBlocking {
                        val ok = mLocalTtsHelper.setEngine(engine)
                        if (!ok) throw Exception(getString(R.string.systts_engine_init_failed_timeout))

                        val data = mLocalTtsHelper.voices.map {
                            VoiceInfo(
                                it.name,
                                it.locale.toLanguageTag(),
                                it.locale.getDisplayName(it.locale),
                                it.features?.toList()
                            )
                        }

                        return@runBlocking AppConst.jsonBuilder.encodeToString(data)
                    }
                }
            })
        }
    }

    override fun startServer() {
        mServer?.start(mCfg.port.toLong())
    }

    override fun closeServer() {
        mServer?.let {
            it.close()
            mLocalTTS?.onDestroy()
            mLocalTTS = null
        }
    }

    private fun getSysTtsEngines(): List<TextToSpeech.EngineInfo> {
        val tts = TextToSpeech(App.context, null)
        val engines = tts.engines
        tts.shutdown()
        return engines
    }

}