package com.notimorph.app

import android.app.Application
import com.notimorph.app.core.AiClient
import com.notimorph.app.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class NotiMorphApp : Application() {

    lateinit var prefs: Prefs
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Live flag maintained by [com.notimorph.app.core.NotiListener]. */
    val listenerConnected = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        // Pre-warm the AI endpoint (DNS + TLS handshake) so the first
        // transformation of a notification is as fast as possible.
        appScope.launch {
            prefs.state.collect { AiClient.warmup(it) }
        }
    }
}
