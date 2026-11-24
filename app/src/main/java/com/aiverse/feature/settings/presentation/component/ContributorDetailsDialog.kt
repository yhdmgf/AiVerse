package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.LocalHyperOSStyle
import com.aiverse.feature.settings.domain.model.Contributor
import com.aiverse.feature.settings.domain.model.ContributorArea
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.Code
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.Edit3
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Layout
import compose.icons.feathericons.Package
import compose.icons.feathericons.Server
import compose.icons.feathericons.Smartphone
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Zap

/**
 * What a face says when it is chosen: who this is, what they worked on, and where to find them.
 *
 * A centered dialog rather than a sheet, laid out like the wall's sibling project does it - a large
 * photo, the name, the handle, a line of prose and then the areas as chips - so the two read as the
 * same idea rather than as two designs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContributorDetailsDialog(
    contributor: Contributor,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val t = LocalUIDensityTokens.current
    val profileUrl = contributor.profileUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(t.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // One face, on screen, drawn large: the only place a picture is worth its full
                // size rather than a step off the wall's own scale.
                ContributorAvatar(
                    contributor = contributor,
                    onScreen = true,
                    targetPx = 512,
                    modifier = Modifier.size(t.scale(LARGE_AVATAR_DP))
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(t.xs)
                ) {
                    Text(
                        text = contributor.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    contributor.login
                        ?.takeIf { !it.equals(contributor.name, ignoreCase = true) }
                        ?.let { login ->
                            Text(
                                text = "@$login",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    Text(
                        text = stringResource(R.string.about_contributors_commits, contributor.commits),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = contributor.note?.takeIf { it.isNotBlank() }
                        ?: contributorSummary(contributor),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (contributor.areas.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(t.sm),
                        verticalArrangement = Arrangement.spacedBy(t.sm)
                    ) {
                        contributor.areas.forEach { ContributionChip(it) }
                    }
                }
            }
        },
        confirmButton = {
            if (profileUrl != null) {
                Surface(
                    onClick = { openUrl(context, profileUrl) },
                    shape = contributorPillShape(),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = t.lg,
                            vertical = t.sm
                        ),
                        horizontalArrangement = Arrangement.spacedBy(t.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = FeatherIcons.ExternalLink,
                            contentDescription = null,
                            modifier = Modifier.size(t.scale(CHIP_ICON_DP))
                        )
                        Text(
                            text = stringResource(R.string.about_contributors_view_github),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_contributors_close))
            }
        }
    )
}

@Composable
private fun contributorSummary(contributor: Contributor): String {
    val areas = contributor.areas.map { stringResource(it.labelRes()) }.joinToString("、")
    return if (areas.isEmpty()) {
        stringResource(R.string.about_contributors_summary_default)
    } else {
        stringResource(R.string.about_contributors_summary_code, areas)
    }
}

@Composable
private fun ContributionChip(area: ContributorArea) {
    val t = LocalUIDensityTokens.current
    Surface(shape = contributorPillShape(), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            modifier = Modifier.padding(horizontal = t.md, vertical = t.sm),
            horizontalArrangement = Arrangement.spacedBy(t.scale(CHIP_GAP_DP)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = area.icon(),
                contentDescription = null,
                tint = area.color,
                modifier = Modifier.size(t.scale(CHIP_ICON_DP))
            )
            Text(
                text = stringResource(area.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 领域标签与 GitHub 按钮的胶囊外形：常规全圆角，HyperOS 档用其小圆角保持体系一致。 */
@Composable
private fun contributorPillShape() =
    if (LocalHyperOSStyle.current) MaterialTheme.shapes.small else RoundedCornerShape(percent = 50)

private fun ContributorArea.icon(): ImageVector = when (this) {
    ContributorArea.APP -> FeatherIcons.Smartphone
    ContributorArea.BUILD -> FeatherIcons.Code
    ContributorArea.DOCS -> FeatherIcons.Book
    ContributorArea.ISSUE -> FeatherIcons.MessageSquare
    ContributorArea.TERMINAL -> FeatherIcons.Terminal
    ContributorArea.PLUGIN -> FeatherIcons.Package
    ContributorArea.GIT -> FeatherIcons.GitBranch
    ContributorArea.EDITOR -> FeatherIcons.Edit3
    ContributorArea.UI -> FeatherIcons.Layout
    ContributorArea.CORE -> FeatherIcons.Cpu
}

private fun ContributorArea.labelRes(): Int = when (this) {
    ContributorArea.APP -> R.string.about_contributors_area_app
    ContributorArea.BUILD -> R.string.about_contributors_area_build
    ContributorArea.DOCS -> R.string.about_contributors_area_docs
    ContributorArea.ISSUE -> R.string.about_contributors_area_issue
    ContributorArea.TERMINAL -> R.string.about_contributors_area_terminal
    ContributorArea.PLUGIN -> R.string.about_contributors_area_plugin
    ContributorArea.GIT -> R.string.about_contributors_area_git
    ContributorArea.EDITOR -> R.string.about_contributors_area_editor
    ContributorArea.UI -> R.string.about_contributors_area_ui
    ContributorArea.CORE -> R.string.about_contributors_area_core
}

private const val LARGE_AVATAR_DP = 104
private const val CHIP_ICON_DP = 18
private const val CHIP_GAP_DP = 6
