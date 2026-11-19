package com.bettermifitness.sync.sync

interface SyncProgressNotifier {
    fun onSyncStarted(total: Int)
    fun onProgress(done: Int, total: Int, currentMetricName: String)
    fun onSyncFinished(success: Boolean, message: String?)
}

object NoOpSyncProgressNotifier : SyncProgressNotifier {
    override fun onSyncStarted(total: Int) = Unit
    override fun onProgress(done: Int, total: Int, currentMetricName: String) = Unit
    override fun onSyncFinished(success: Boolean, message: String?) = Unit
}
