/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.evolution.settings.fragments.miscellaneous

import android.app.ActivityManager
import android.app.AxSandboxManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BankingCompatibilitySettings : SettingsPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.banking_compatibility_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ExpressiveTheme {
                BankingCompatibilityContent(context = requireContext())
            }
        }
    }

    override fun getMetricsCategory(): Int = MetricsEvent.EVOLVER

    companion object {
        private const val BANKING_COMPATIBILITY = "banking_compatibility"
    }
}

private data class BankingAppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val isSelected: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankingCompatibilityContent(context: Context) {
    val scope = rememberCoroutineScope()
    val sandboxManager = remember {
        context.getSystemService(Context.AX_SANDBOX_SERVICE) as? AxSandboxManager
    }
    val activityManager = remember {
        context.getSystemService(ActivityManager::class.java)
    }

    val allApps = remember { mutableStateListOf<BankingAppItem>() }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)

            installed.mapNotNull { appInfo ->
                if (appInfo.packageName == context.packageName) {
                    return@mapNotNull null
                }
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isSelected = runCatching {
                    sandboxManager?.isSpoofSettingEnabled(
                        appInfo.packageName, "banking_compatibility", context.userId)
                }.getOrNull() == true
                BankingAppItem(
                    packageName = appInfo.packageName,
                    label = label,
                    icon = icon,
                    isSystem = isSystem,
                    isSelected = isSelected,
                )
            }.sortedWith(
                compareByDescending<BankingAppItem> { it.isSelected }
                    .thenBy { it.label.lowercase() }
            )
        }
        allApps.clear()
        allApps.addAll(loaded)
        isLoading = false
    }

    val activeCount = allApps.count { it.isSelected }
    val filteredApps = allApps.filter { app ->
        (showSystemApps || !app.isSystem) &&
            (searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true))
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            ExpressiveHeaderCard(
                title = stringResource(R.string.banking_compatibility_title),
                subtitle = if (activeCount == 0)
                    stringResource(R.string.banking_compatibility_none_configured)
                else
                    "$activeCount apps selected",
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppPickerSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text(stringResource(R.string.show_system_apps)) },
                    leadingIcon = if (showSystemApps) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                )

                OutlinedButton(
                    onClick = {
                        val selectedPackages = allApps.filter { it.isSelected }
                            .map { it.packageName }
                            .toSet()
                        allApps.indices.forEach { index ->
                            allApps[index] = allApps[index].copy(isSelected = false)
                        }
                        scope.launch(Dispatchers.IO) {
                            selectedPackages.forEach { packageName ->
                                runCatching {
                                    sandboxManager?.setSpoofSettingEnabled(
                                        packageName, "banking_compatibility", false, context.userId)
                                }
                                syncTrickyStoreTarget(context, packageName, false)
                            }
                            killPackages(activityManager, selectedPackages)
                        }
                    },
                    enabled = activeCount > 0,
                ) {
                    Text(stringResource(R.string.clear))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                ExpressiveLoadingBox(modifier = Modifier.weight(1f))
            } else if (filteredApps.isEmpty()) {
                ExpressiveEmptyState(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.app_spoofing_no_apps_available),
                    description = stringResource(R.string.banking_compatibility_empty_description),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppPickerItem(
                            packageName = app.packageName,
                            label = app.label,
                            icon = app.icon,
                            isSystem = app.isSystem,
                            checked = app.isSelected,
                            enabled = sandboxManager != null,
                            onToggle = { enabled ->
                                val index = allApps.indexOfFirst {
                                    it.packageName == app.packageName
                                }
                                if (index < 0) return@AppPickerItem
                                allApps[index] = allApps[index].copy(isSelected = enabled)
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        sandboxManager?.setSpoofSettingEnabled(
                                            app.packageName, "banking_compatibility", enabled, context.userId)
                                    }
                                    syncTrickyStoreTarget(context, app.packageName, enabled)
                                    stopPackage(activityManager, app.packageName)
                                }
                            },
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.banking_compatibility_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp, 12.dp, 8.dp, 80.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keep TrickyStore targets aligned with the banking preset. Payment apps such
 * as Paytm generate their own Keystore keys and exit when TEE attestation is
 * unprovisioned; AUTO lets TrickyStore fall back to the keybox.
 *
 * Defaults (GMS, Paytm, …) are never removed here so a reset of the banking
 * list cannot strip required attestation targets.
 */
private fun syncTrickyStoreTarget(
    context: Context,
    packageName: String,
    enabled: Boolean,
) {
    val resolver = context.contentResolver
    val current = Settings.Secure.getString(resolver, TrickyStoreAppSettings.TARGET_KEY)
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toMutableList()
        ?: mutableListOf()

    val names = current.map { it.trimEnd('?', '!', '-') }.toSet()
    if (enabled) {
        if (packageName !in names) {
            current.add(packageName)
        }
    } else if (packageName !in TrickyStoreAppSettings.DEFAULT_TARGETS) {
        current.removeAll { it.trimEnd('?', '!', '-') == packageName }
    }

    Settings.Secure.putString(
        resolver,
        TrickyStoreAppSettings.TARGET_KEY,
        current.joinToString("\n"),
    )
}
