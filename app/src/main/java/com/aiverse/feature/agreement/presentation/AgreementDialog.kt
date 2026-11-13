package com.aiverse.feature.agreement.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.aiverse.R
import com.aiverse.app.util.TextCipher
import com.aiverse.core.theme.semanticColors

@Composable
fun AgreementDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    // 测试版（applicationId 以 .beta 结尾，见 beta buildType）单独提示版本性质，正式版文案保持原样。
    val context = LocalContext.current
    val isBeta = remember(context) {
        context.applicationContext.packageName.endsWith(".beta")
    }
    val titleRes = if (isBeta) R.string.agreement_test_title else R.string.agreement_title
    val termsUrl = stringResource(R.string.agreement_terms_url)
    val privacyUrl = stringResource(R.string.agreement_privacy_url)
    val downloadUrl = stringResource(R.string.agreement_test_download_url)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.semanticColors.info,
            textDecoration = TextDecoration.Underline
        )
    )
    val notice = buildAnnotatedString {
        if (isBeta) {
            append(stringResource(R.string.agreement_test_notice))
            withLink(
                LinkAnnotation.Clickable("download", linkStyles) {
                    runCatching { uriHandler.openUri(downloadUrl) }
                }
            ) {
                append(stringResource(R.string.agreement_test_download))
            }
            append("\n\n")
        }
        append(stringResource(R.string.agreement_notice_prefix))
        withLink(
            LinkAnnotation.Clickable("terms", linkStyles) {
                runCatching { uriHandler.openUri(termsUrl) }
            }
        ) {
            append(stringResource(R.string.agreement_terms_link))
        }
        append(stringResource(R.string.agreement_notice_conjunction))
        withLink(
            LinkAnnotation.Clickable("privacy", linkStyles) {
                runCatching { uriHandler.openUri(privacyUrl) }
            }
        ) {
            append(stringResource(R.string.agreement_privacy_link))
        }
        append(stringResource(R.string.agreement_notice_suffix))
        // 官网 / 仓库 链接：URL 密文存储、运行时解密，避免明文出现在代码与资源中
        append("\n")
        append(TextCipher.labelOfficial + ": ")
        withLink(
            LinkAnnotation.Clickable("official", linkStyles) {
                runCatching { uriHandler.openUri(TextCipher.officialUrl) }
            }
        ) {
            append(TextCipher.officialUrl)
        }
        append("\n")
        append(TextCipher.labelRepo + ": ")
        withLink(
            LinkAnnotation.Clickable("repo", linkStyles) {
                runCatching { uriHandler.openUri(TextCipher.repoUrl) }
            }
        ) {
            append(TextCipher.repoUrl)
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(titleRes)) },
        text = {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.agreement_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.agreement_decline))
            }
        }
    )
}
