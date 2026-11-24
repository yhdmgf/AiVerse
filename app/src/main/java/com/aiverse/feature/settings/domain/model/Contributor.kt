package com.aiverse.feature.settings.domain.model

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which part of the project a contributor's commits actually touched.
 *
 * A badge on the wall is drawn from one of these, so a badge can never disagree with the wording of
 * the person's summary. They are not a claim about what someone can do - they are read off the paths
 * their commits changed, by the contributors workflow, and the [key] is the value that workflow
 * writes into the snapshot's `areas` field.
 */
enum class ContributorArea(val key: String, val color: Color) {
    APP("APP", AreaColorApp),
    SERVER("SERVER", AreaColorServer),
    ADB("ADB", AreaColorAdb),
    BUILD("BUILD", AreaColorBuild),
    I18N("I18N", AreaColorI18n),
    DOCS("DOCS", AreaColorDocs),
    AGENT("AGENT", AreaColorAgent),
    TERMINAL("TERMINAL", AreaColorTerminal),
    PLUGIN("PLUGIN", AreaColorPlugin),
    GIT("GIT", AreaColorGit),
    EDITOR("EDITOR", AreaColorEditor),
    UI("UI", AreaColorUi),
    CORE("CORE", AreaColorCore)
}

/**
 * The colour a domain's badge is drawn in, one per domain and no two alike, so the wall reads each
 * badge by its colour before its label. Held here rather than in the dialog so the enum - not the
 * caller - owns the pairing of a domain with the colour that stands for it.
 *
 * Colors are defined as hex constants and would benefit from migration to MaterialTheme in a
 * follow-up PR to support proper dark/light theme adaptation. Current colors are readable in both
 * themes but may have suboptimal contrast in some edge cases.
 */
private val AreaColorApp = Color(0xFF3DDC84)
private val AreaColorServer = Color(0xFF4C8DFF)
private val AreaColorAdb = Color(0xFF9575CD)
private val AreaColorBuild = Color(0xFFF9A825)
private val AreaColorI18n = Color(0xFFEF6C00)
private val AreaColorDocs = Color(0xFF26A69A)
private val AreaColorAgent = Color(0xFF26C6DA)
private val AreaColorTerminal = Color(0xFFB388FF)
private val AreaColorPlugin = Color(0xFFEC407A)
private val AreaColorGit = Color(0xFFFF7043)
private val AreaColorEditor = Color(0xFFFFCA28)
private val AreaColorUi = Color(0xFF7E57C2)
private val AreaColorCore = Color(0xFF5C6BC0)

/**
 * Someone credited on the wall.
 *
 * [login] and [avatarUrl] come from the GitHub account behind a commit identity, where there is
 * one. Neither is guessed - the obvious guess for one contributor now belongs to a different
 * person, and a credit that opens a stranger's profile is worse than a credit with no link - so
 * where there is no account the wall draws the person's initial instead.
 *
 * The picture is the account's own, at the URL it lives at; nothing is carried in the app, so what
 * is drawn is whatever that person is using now rather than a copy from whenever the credits were
 * last generated.
 */
data class Contributor(
    val name: String,
    val login: String?,
    val avatarUrl: String?,
    val commits: Int,
    val areas: List<ContributorArea>,
    /** A "what they did" line written by hand for contributors credited off the workflow; null otherwise. */
    val note: String?
) {
    val profileUrl: String? get() = login?.let { "https://github.com/$it" }
}

/**
 * Read a snapshot.
 *
 * Unknown area names are dropped rather than treated as an error, so a newer snapshot that adds one
 * still renders on an older app instead of taking the wall down with it. An entry without a name is
 * skipped: the wall always labels a face, so there is nothing to draw for one.
 */
fun parseContributors(payload: String): List<Contributor> {
    val array = JSONObject(payload).optJSONArray("contributors") ?: return emptyList()

    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optionalText("name") ?: continue

            val areas = item.optJSONArray("areas").toStringList().mapNotNull { key ->
                ContributorArea.entries.firstOrNull { it.key == key }
            }

            add(
                Contributor(
                    name = name,
                    login = item.optionalText("login"),
                    avatarUrl = item.optionalText("avatarUrl"),
                    commits = item.optInt("commits"),
                    areas = areas,
                    note = item.optionalText("note")
                )
            )
        }
    }
}

/**
 * A text field of the snapshot, or null where the generator wrote a null.
 *
 * Not `optString`: Android answers a JSON null with the *word* "null", which is then a value
 * everywhere it is used - every contributor without a bundled picture was cached under one key
 * made of that word, so the first of them was drawn for all of them, and one without an account
 * was offered a link to github.com/null. (The org.json the tests run against answers "" instead,
 * which is why this has to be written out rather than left to the library.)
 */
private fun JSONObject.optionalText(key: String): String? {
    if (isNull(key)) return null
    val value = optString(key).trim()
    // The word itself is not a value either, whichever library answered with it.
    return if (value.isEmpty() || value.equals("null", ignoreCase = true)) null else value
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotEmpty()) add(value)
        }
    }
}
