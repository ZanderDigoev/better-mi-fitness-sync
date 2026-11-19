package com.bettermifitness.sync.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.i18n.L10n
import com.bettermifitness.sync.theme.BrandColors
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.components.AmbientScreenBackground
import com.bettermifitness.sync.ui.components.GlassCard
import com.bettermifitness.sync.ui.components.PrimaryButton
import com.bettermifitness.sync.ui.components.StickyCtaBar
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons

/**
 * Home hub (parity with iOS SwiftUI HomeView):
 * account hero → optional banner → Setup → Activity → sticky Sync CTA.
 * No live health values — those live in Health Connect / Apple Health.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLogout()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
        viewModel.refreshHealthReadiness()
    }

    val healthName = state.healthServiceName.ifBlank { L10n.string(L10n.healthFallback) }
    val fullName = state.profile?.result?.name?.trim()?.takeIf { it.isNotEmpty() }
    val titleName = fullName ?: L10n.string(L10n.homeAccount)
    val canUpdate = state.prefsReady && state.enabledMetricsCount > 0

    val hasAuthError = state.profileError?.contains("70016") == true ||
        state.profileError?.contains("sign in", ignoreCase = true) == true ||
        (state.lastSyncIsError && (state.lastSyncDetail.contains("70016") || state.lastSyncDetail.contains("sign in", ignoreCase = true)))

    val primaryLabel = when {
        hasAuthError -> L10n.string(L10n.outcomeSignInAgain)
        !state.prefsReady -> L10n.string(L10n.homeSyncOptions)
        state.healthNeedsAction -> L10n.string(L10n.healthAllowAccess)
        !canUpdate -> L10n.string(L10n.homeOpenSettings)
        state.isSyncing -> L10n.string(L10n.homeSyncing)
        else -> L10n.string(L10n.homeSyncNow)
    }

    AmbientScreenBackground {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            AppIcon(
                                AppIcons.Settings,
                                contentDescription = L10n.string(L10n.homeSettingsAccessibility),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                StickyCtaBar(modifier = Modifier.navigationBarsPadding()) {
                    PrimaryButton(
                        text = primaryLabel,
                        onClick = {
                            when {
                                hasAuthError -> {
                                    viewModel.logout()
                                    onLogout()
                                }
                                state.healthNeedsAction -> viewModel.openHealthService()
                                !canUpdate -> onSettingsClick()
                                state.isSyncing -> onSyncClick()
                                else -> {
                                    viewModel.startSyncIfIdle()
                                    onSyncClick()
                                }
                            }
                        },
                        loading = state.isSyncing,
                        icon = if (hasAuthError || (!canUpdate && !state.healthNeedsAction)) null else AppIcons.Sync,
                    )
                }
            },
        ) { padding ->
            val bottomPadding = padding.calculateBottomPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BrandSpacing.ScreenHorizontal)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AccountHero(
                    name = titleName,
                    subtitle = when {
                        state.profileError != null -> state.profileError ?: L10n.string(L10n.homeSignedIn)
                        state.profile != null -> L10n.string(L10n.homeConnected)
                        else -> L10n.string(L10n.homeLoadingProfile)
                    },
                    isError = state.profileError != null,
                    isSyncing = state.isSyncing,
                )

                blockingBanner(state, healthName)?.let { banner ->
                    BlockingBannerCard(banner)
                }

                SetupSection(
                    state = state,
                    healthName = healthName,
                    onHealthClick = {
                        if (state.healthNeedsAction) viewModel.openHealthService()
                    },
                    onSyncOptionsClick = onSettingsClick,
                )

                ActivitySection(state = state, onClick = onSyncClick)

                Spacer(Modifier.height(bottomPadding + 20.dp))
            }
        }
    }
}

@Composable
private fun AccountHero(name: String, subtitle: String, isError: Boolean, isSyncing: Boolean) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        LetterAvatar(letter, isSyncing)
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Initial-letter circle with gentle pulsing halo during sync.
@Composable
private fun LetterAvatar(letter: String, isSyncing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val pulseScale by if (isSyncing) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_scale",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val pulseAlpha by if (isSyncing) {
        infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(88.dp),
    ) {
        if (isSyncing) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(BrandColors.OrangeBright),
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BrandColors.Navy),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

private data class Banner(val title: String, val detail: String, val error: Boolean)

private fun blockingBanner(state: HomeUiState, healthName: String): Banner? {
    val hasAuthError = state.profileError?.contains("70016") == true ||
        state.profileError?.contains("sign in", ignoreCase = true) == true ||
        (state.lastSyncIsError && (state.lastSyncDetail.contains("70016") || state.lastSyncDetail.contains("sign in", ignoreCase = true)))
    if (hasAuthError) {
        return Banner(
            title = L10n.text(L10n.outcomeSignInAgain),
            detail = "Сессия Mi Аккаунта истекла (код 70016). Нажмите кнопку внизу для повторного входа.",
            error = true,
        )
    }
    if (state.healthNeedsAction) {
        return Banner(
            title = state.healthStatusTitle.ifBlank {
                L10n.textFmt(L10n.openHealth, healthName)
            },
            detail = state.healthStatusDetail.ifBlank {
                L10n.textFmt(L10n.homeAllowAccessDetail, healthName)
            },
            error = true,
        )
    }
    if (state.enabledMetricsCount == 0) {
        return Banner(
            title = L10n.text(L10n.homeNothingSelectedTitle),
            detail = L10n.textFmt(L10n.homeNothingSelectedDetail, healthName),
            error = false,
        )
    }
    return null
}

@Composable
private fun BlockingBannerCard(banner: Banner) {
    val accent = if (banner.error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                banner.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                banner.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupSection(
    state: HomeUiState,
    healthName: String,
    onHealthClick: () -> Unit,
    onSyncOptionsClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(L10n.string(L10n.homeSetup))
        GlassCard(shape = RoundedCornerShape(14.dp)) {
            Column {
                SetupRow(
                    icon = AppIcons.Favorite,
                    badgeColor = if (state.healthNeedsAction) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFFFF2D55) // Health-like pink
                    },
                    title = healthName,
                    trailing = when {
                        state.healthNeedsAction -> L10n.string(L10n.healthAllowAccess)
                        state.healthReady -> L10n.string(L10n.healthReady)
                        else -> state.healthStatusTitle.ifBlank { L10n.string(L10n.healthChecking) }
                    },
                    trailingColor = if (state.healthNeedsAction) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    showChevron = state.healthNeedsAction,
                    onClick = if (state.healthNeedsAction) onHealthClick else null,
                )
                HorizontalDivider(Modifier.padding(start = 52.dp))
                SetupRow(
                    icon = AppIcons.Tune,
                    badgeColor = if (state.enabledMetricsCount == 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        BrandColors.Navy
                    },
                    title = L10n.string(L10n.homeSyncOptions),
                    detail = planSummary(state),
                    detailColor = if (state.enabledMetricsCount == 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    showChevron = true,
                    onClick = onSyncOptionsClick,
                )
            }
        }
    }
}

private fun planSummary(state: HomeUiState): String {
    if (!state.prefsReady) return L10n.text(L10n.settingsSyncOptionsLoading)
    if (state.enabledMetricsCount == 0) return L10n.text(L10n.homeNoneSelected)
    val metrics = if (state.enabledMetricsCount == 1) {
        L10n.text(L10n.homeMetricOne)
    } else {
        L10n.textFmt(L10n.homeMetricMany, state.enabledMetricsCount)
    }
    val days = if (state.rangeDays == 1) L10n.text(L10n.homeDayOne) else L10n.textFmt(L10n.homeDayMany, state.rangeDays)
    val auto = if (state.autoSync) L10n.text(L10n.homeAutoOn) else L10n.text(L10n.homeManual)
    return "$metrics · $days · $auto"
}

@Composable
private fun SetupRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    badgeColor: Color,
    title: String,
    detail: String? = null,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showChevron: Boolean,
    onClick: (() -> Unit)?,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = detailColor,
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = trailingColor,
                maxLines = 1,
            )
        }
        if (showChevron) {
            AppIcon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ActivitySection(state: HomeUiState, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(L10n.string(L10n.homeActivity))
        GlassCard(shape = RoundedCornerShape(14.dp), onClick = onClick) {
            Column {
                FootnoteRow(
                    title = L10n.string(L10n.homeLastSync),
                    value = lastSyncValue(state),
                    valueColor = lastSyncColor(state),
                    showChevron = true,
                )
                HorizontalDivider(Modifier.padding(start = 14.dp))
                FootnoteRow(
                    title = L10n.string(L10n.homeBackgroundSync),
                    value = backgroundValue(state),
                    valueColor = if (state.lastBackgroundIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    showChevron = true,
                )
            }
        }
    }
}

private fun lastSyncValue(state: HomeUiState): String {
    if (state.isSyncing) return L10n.text(L10n.homeSyncing)
    val whenLabel = state.lastSyncLabel
    if (whenLabel == L10n.text(L10n.homeNever)) return L10n.text(L10n.homeNever)
    if (state.lastSyncIsError) return L10n.textFmt(L10n.homeFailedSuffix, whenLabel)
    if (state.lastSyncIsWarning) return L10n.textFmt(L10n.homePartialSuffix, whenLabel)
    return whenLabel
}

@Composable
private fun lastSyncColor(state: HomeUiState): Color = when {
    state.isSyncing -> MaterialTheme.colorScheme.primary
    state.lastSyncIsError -> MaterialTheme.colorScheme.error
    state.lastSyncIsWarning -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun backgroundValue(state: HomeUiState): String {
    val label = state.lastBackgroundLabel
    return if (state.lastBackgroundIsError) L10n.textFmt(L10n.homeFailedSuffix, label) else label
}

@Composable
private fun FootnoteRow(
    title: String,
    value: String,
    valueColor: Color,
    showChevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showChevron) {
                AppIcon(
                    AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}
