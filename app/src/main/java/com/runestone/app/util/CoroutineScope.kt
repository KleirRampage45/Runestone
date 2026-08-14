package com.runestone.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val main: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val default: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
