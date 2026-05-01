package com.example.dualmapper.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ServiceScopes {
    fun createServiceScope(): CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
}