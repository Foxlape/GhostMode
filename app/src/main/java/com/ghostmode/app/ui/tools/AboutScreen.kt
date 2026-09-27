package com.ghostmode.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ghostmode.app.BuildConfig
import com.ghostmode.app.R
import com.ghostmode.app.support.UpdateChecker
import com.ghostmode.app.ui.components.CodeBlock
import com.ghostmode.app.ui.components.ScreenPadding
import com.ghostmode.app.ui.components.SectionHeader
import com.ghostmode.app.ui.components.SettingsGroup
import com.ghostmode.app.ui.components.SettingsRow
import com.ghostmode.app.ui.components.SubScreenTopBar

/** SHA-256 fingerprint of the key that signs official GitHub releases. */
const val RELEASE_CERT_SHA256 =
    "FB:2A:E9:C4:80:BB:0F:04:55:65:F7:B5:CA:BF:01:7D:98:18:21:A9:33:F0:78:53:DD:47:12:28:D5:71:B0:50"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val copy = com.ghostmode.app.ui.components.rememberCopyToClipboard()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SubScreenTopBar(stringResource(R.string.about_title), onBack, scrollBehavior) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6A4DF0), Color(0xFF221A4F)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_ghost), null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeader(stringResource(R.string.about_links))
            SettingsGroup {
                SettingsRow(Icons.Outlined.Code, stringResource(R.string.about_source), "github.com/Foxlape/GhostMode", { onOpenUrl(UpdateChecker.REPOSITORY_URL) })
                Divider()
                SettingsRow(Icons.Outlined.NewReleases, stringResource(R.string.about_releases), null, { onOpenUrl(UpdateChecker.RELEASES_URL) })
                Divider()
                SettingsRow(Icons.Outlined.BugReport, stringResource(R.string.about_report_bug), null, { onOpenUrl("${UpdateChecker.REPOSITORY_URL}/issues/new/choose") })
                Divider()
                SettingsRow(Icons.Outlined.Description, stringResource(R.string.about_changelog), null, { onOpenUrl("${UpdateChecker.REPOSITORY_URL}/blob/main/CHANGELOG.md") })
                Divider()
                SettingsRow(Icons.Outlined.Gavel, stringResource(R.string.about_license), "Apache License 2.0", { onOpenUrl("${UpdateChecker.REPOSITORY_URL}/blob/main/LICENSE") })
            }

            SectionHeader(stringResource(R.string.about_cert_label))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Outlined.Verified,
                    title = stringResource(R.string.about_cert_title),
                    summary = stringResource(R.string.about_cert_summary),
                    onClick = { copy(RELEASE_CERT_SHA256) },
                    trailing = null
                )
                CodeBlock(RELEASE_CERT_SHA256, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }

            Text(
                stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(
    modifier = Modifier.padding(start = 66.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)
