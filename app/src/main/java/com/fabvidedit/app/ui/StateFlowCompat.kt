package com.fabvidedit.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun <T> StateFlow<T>.collectAsStateWithLifecycleCompat(): State<T> = collectAsState()

