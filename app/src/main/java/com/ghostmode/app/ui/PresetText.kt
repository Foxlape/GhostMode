package com.ghostmode.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ghostmode.app.data.Preset

@Composable
fun presetTitle(preset: Preset): String =
    if (preset.titleRes != 0) stringResource(preset.titleRes) else preset.title

@Composable
fun presetDescription(preset: Preset): String =
    if (preset.descriptionRes != 0) stringResource(preset.descriptionRes) else preset.description
