package com.aiverse.feature.editor.presentation.component

import com.aiverse.feature.editor.presentation.MarkdownPreviewViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewRequestPolicyTest {

    @Test
    fun dataMainDocumentIsNotBlocked() {
        // loadDataWithBaseURL 的主文档就是 data: URL，host 为空；拦掉会让预览整页加载失败。
        assertFalse(isPreviewRequestBlocked("data", null))
        assertFalse(isPreviewRequestBlocked("data", ""))
    }

    @Test
    fun pageLocalSchemesAreNotBlocked() {
        assertFalse(isPreviewRequestBlocked("blob", null))
        assertFalse(isPreviewRequestBlocked("about", null))
    }

    @Test
    fun virtualAssetOriginIsNotBlocked() {
        assertFalse(isPreviewRequestBlocked("https", MarkdownPreviewViewModel.ASSET_HOST))
    }

    @Test
    fun externalNetworkAndLocalFileSchemesAreBlocked() {
        assertTrue(isPreviewRequestBlocked("https", "example.com"))
        assertTrue(isPreviewRequestBlocked("http", MarkdownPreviewViewModel.ASSET_HOST))
        assertTrue(isPreviewRequestBlocked("file", null))
        assertTrue(isPreviewRequestBlocked(null, null))
    }
}
