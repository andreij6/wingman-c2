package com.wingmanc2.core.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun UserMessageHost(message: String?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message)
            onShown()
        }
    }
    SnackbarHost(host, modifier)
}
