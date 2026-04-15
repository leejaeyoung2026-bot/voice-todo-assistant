package com.ultron.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ultron.app.service.UltronListenerService
import com.ultron.app.worker.RetryWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, UltronListenerService::class.java)
            context.startForegroundService(serviceIntent)
            RetryWorker.enqueuePeriodicRetry(context)
        }
    }
}
