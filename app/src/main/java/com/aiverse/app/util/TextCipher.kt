package com.aiverse.app.util

import android.util.Base64

internal object TextCipher {

    // 59 4F A3 11 7E D8 3C 9B 06 F2 68 1D 90 5A B7 4C
    private val K = intArrayOf(89, 79, 163, 17, 126, 216, 60, 155, 6, 242, 104, 29, 144, 90, 183, 76)

    fun dec(cipher: String): String = try {
        val data = Base64.decode(cipher, Base64.NO_WRAP)
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor K[i % K.size]).toByte()
        String(out, Charsets.UTF_8)
    } catch (t: Throwable) { "" }

    fun enc(text: String): String {
        val raw = text.toByteArray(Charsets.UTF_8)
        val out = ByteArray(raw.size)
        for (i in raw.indices) out[i] = (raw[i].toInt() xor K[i % K.size]).toByte()
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private const val D_OFFICIAL_URL = "MTvXYQ3iE7Rnmx584CqZLzdg"
    private const val D_REPO_URL     = "MTvXYQ3iE7Rhmxx15TiZLzYijGgWvFH8YN0JdOY/xT88"
    private const val D_LABEL_OFFICIAL = "vOE79sNJ"
    private const val D_LABEL_REPO     = "vfQw9MRL"

    val officialUrl  get() = dec(D_OFFICIAL_URL)
    val repoUrl      get() = dec(D_REPO_URL)
    val labelOfficial get() = dec(D_LABEL_OFFICIAL)
    val labelRepo    get() = dec(D_LABEL_REPO)
}
