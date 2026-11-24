package com.aiverse.feature.settings.presentation.component

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import coil.request.ImageRequest
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.feature.settings.data.remote.ContributorAvatars
import com.aiverse.feature.settings.domain.model.Contributor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The wall itself. The face size, the gap and the interaction are ported from Duck Detector
 * (Apache-2.0, Eltavine): the way a wall of faces reads depends on all of them together.
 *
 * What is not theirs is where it opens. Their wall is thirty faces and a fixed 1.5x, and at their
 * size a fixed zoom is the same as a sensible one. This wall is a hundred and forty faces, so the
 * same number would draw every one of them as a dot, and fitting all of them instead is worse: a
 * honeycomb of specks is not a wall of people. So the opening zoom is derived - enough of it that
 * the faces in the middle are a readable size - and the rest of the wall is reached by dragging
 * and pinching, which is what the interaction was always for.
 */

/** Zoom is a multiplier on the fit, and this is the value that means "not chosen yet". */
private const val UnsetZoom = 0f

/**
 * How much of a face to draw on opening.
 *
 * One would draw a face at the size it was laid out, which is readable; just under that leaves a
 * little of the surrounding wall visible at the edges.
 */
private const val OpeningFaceFraction = 0.85f

private const val MinWallZoom = 1f
private const val MaxWallZoom = 5f

/** Faces near the border are bent inwards at all, but this keeps them from being sliced. */
private const val WallFitMargin = 0.94f

/** How often the wall works out which faces are on screen. See the loop below for why. */
private const val VISIBILITY_POLL_MS = 150L

private val WallOffsetSaver = listSaver<Offset, Float>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

/**
 * The wall of faces: drag to move it, pinch to zoom, tap one for details.
 *
 * Every face is a focusable, clickable control as well, which is what lets the same wall be used
 * with a remote on a television rather than only by touch.
 */
@Composable
internal fun ContributorWall(
    contributors: List<Contributor>,
    onSelect: (Contributor) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalUIDensityTokens.current
    var userZoom by rememberSaveable { mutableFloatStateOf(UnsetZoom) }
    var offset by rememberSaveable(stateSaver = WallOffsetSaver) { mutableStateOf(Offset.Zero) }
    var motionJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val density = LocalDensity.current

    val avatarSize = t.scale(AVATAR_SIZE_DP)
    val avatarGap = t.scale(AVATAR_GAP_DP)
    val canvasMaxHeight = t.scale(CANVAS_MAX_HEIGHT_DP)
    val insetPx = with(density) { t.scale(CANVAS_INSET_DP).toPx() }

    val geometry = remember(contributors.size, density, avatarSize, avatarGap) {
        with(density) {
            contributorWallGeometry(contributors.size, avatarSize.toPx(), avatarGap.toPx())
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val canvasHeight = maxWidth.coerceAtMost(canvasMaxHeight)
        val viewport = with(density) { Size(maxWidth.toPx(), canvasHeight.toPx()) }
        val available = Size(viewport.width - insetPx * 2, viewport.height - insetPx * 2)
        val fitScale = geometry.fitScale(available) * WallFitMargin
        val center = Offset(viewport.width / 2, viewport.height / 2)

        // How big a face comes out at depends on how many there are, so the zoom that opens on a
        // readable patch of wall is derived from the fit rather than fixed. It is not stored, so a
        // rotation or a change in the number of contributors re-derives it until the user takes
        // over by pinching.
        val openingZoom = (OpeningFaceFraction / fitScale)
            .coerceIn(MinWallZoom, MaxWallZoom)

        // Read through the state instead of capturing the value: the gesture and the visibility
        // loop below both outlive several recompositions, and a captured zoom would leave them
        // holding a pinch and decoding faces against the zoom this wall happened to open at.
        fun zoom() = if (userZoom == UnsetZoom) openingZoom else userZoom

        // Which faces are on screen decides whose picture is worth fetching, and asking that
        // question per frame would cost more than the fetching it saves - the answer only changes
        // when the wall is moved. The size is bucketed to powers of two, so a pinch re-decodes a
        // face four times at most instead of on every frame of the gesture.
        //
        // The first answer is the real one rather than "all of them": a wall whose every index
        // starts out marked visible starts every decode at once, including the ones behind the
        // clip - each of those a file read and a decode on the way to drawing somebody's initial.
        var avatarPx by remember(geometry, viewport) { mutableIntStateOf(64) }
        var visible by remember(geometry, viewport) {
            val scale = fitScale * zoom()
            mutableStateOf(
                onScreenFaces(
                    geometry = geometry,
                    viewport = viewport,
                    scale = scale,
                    offset = offset,
                    reach = with(density) { avatarSize.toPx() } * scale
                )
            )
        }

        LaunchedEffect(geometry, viewport) {
            while (true) {
                val scale = fitScale * zoom()
                val onScreen = onScreenFaces(
                    geometry = geometry,
                    viewport = viewport,
                    scale = scale,
                    offset = offset,
                    reach = with(density) { avatarSize.toPx() } * scale
                )
                if (onScreen != visible) visible = onScreen

                val wanted = with(density) { avatarSize.toPx() * scale }.roundToInt()
                val bucketed = avatarBucket(wanted)
                if (bucketed != avatarPx) avatarPx = bucketed

                delay(VISIBILITY_POLL_MS)
            }
        }

        // A saved offset can belong to a different viewport - a rotation, or a resized window - and
        // has to be brought back inside before it is drawn.
        LaunchedEffect(geometry, viewport) {
            motionJob?.cancel()
            offset = constrainWallOffset(offset, geometry.panLimit(available, fitScale * zoom()))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .clip(MaterialTheme.shapes.large)
                .wallEdgeFade()
                .pointerInput(geometry, viewport) {
                    val maximumVelocity = t.scale(MAX_VELOCITY_DP).toPx()
                        .let { Velocity(it, it) }
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        // Touching a wall that is still coasting stops it, which is the only way to
                        // catch it: a fling can outlast the eye.
                        val stoppingMotion = motionJob?.isActive == true
                        motionJob?.cancel()
                        if (stoppingMotion) down.consume()

                        val tracker = VelocityTracker()
                        var trackedPosition = Offset.Zero
                        var pointerCount = 1
                        tracker.addPointerInputChange(down)
                        var rawOffset = unresistWallOffset(
                            offset,
                            geometry.panLimit(available, fitScale * zoom()),
                            viewport
                        )
                        var velocityHandoff: WallVelocityHandoff? = null
                        var releaseTimeMillis = down.uptimeMillis
                        var drag = Offset.Zero
                        var ownsGesture = stoppingMotion

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pan = event.calculatePan()
                            val pressedCount = event.changes.count { it.pressed }
                            val previousEventTimeMillis = releaseTimeMillis
                            releaseTimeMillis = event.changes.first().uptimeMillis

                            // Lifting one finger of a pinch: keep the speed it had, but only for
                            // the next event or two.
                            if (pressedCount > 0 && pressedCount != pointerCount) {
                                velocityHandoff = if (pointerCount > 1 && pressedCount == 1) {
                                    val velocity = tracker.calculateVelocity(maximumVelocity)
                                    WallVelocityHandoff(
                                        Offset(velocity.x, velocity.y),
                                        previousEventTimeMillis
                                    )
                                } else null
                                tracker.resetTracking()
                                trackedPosition = Offset.Zero
                            }
                            if (pressedCount == 1 && pan != Offset.Zero) velocityHandoff = null

                            if (pressedCount == 1 || pressedCount == 0 && pointerCount == 1) {
                                val pointer = event.changes.firstOrNull { it.pressed }
                                    ?: event.changes.first { it.previousPressed }
                                tracker.addPointerInputChange(pointer)
                            } else if (pressedCount > 1) {
                                // Two fingers: track their midpoint rather than a single finger.
                                trackedPosition += pan
                                tracker.addPosition(event.changes.first().uptimeMillis, trackedPosition)
                            }
                            pointerCount = pressedCount
                            drag += pan
                            if (pressedCount >= 2 || drag.getDistance() > viewConfiguration.touchSlop) {
                                ownsGesture = true
                            }
                            if (ownsGesture) {
                                val current = zoom()
                                val nextZoom = (current * event.calculateZoom())
                                    .coerceIn(MinWallZoom, MaxWallZoom)
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (centroid != Offset.Unspecified) {
                                    rawOffset = zoomAround(rawOffset, centroid - center, pan, nextZoom / current)
                                }
                                userZoom = nextZoom
                                offset = resistWallOffset(
                                    rawOffset,
                                    geometry.panLimit(available, fitScale * nextZoom),
                                    viewport
                                )
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (ownsGesture) {
                            val velocity = tracker.calculateVelocity(maximumVelocity)
                            val releasedVelocity = velocityHandoff?.velocityAtRelease(releaseTimeMillis)
                                ?: Offset(velocity.x, velocity.y)
                            // Below a flick's worth of speed, treat the release as holding still.
                            val initialVelocity = releasedVelocity.let {
                                if (it.getDistance() < t.scale(MIN_FLING_VELOCITY_DP).toPx()) {
                                    Offset.Zero
                                } else {
                                    it
                                }
                            }
                            motionJob = scope.launch {
                                settleContributorWall(
                                    initialOffset = offset,
                                    initialVelocity = initialVelocity,
                                    limit = geometry.panLimit(available, fitScale * zoom()),
                                    viewport = viewport,
                                    decay = decay
                                ) { offset = it }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            contributors.forEachIndexed { index, contributor ->
                // Every face stays composed, including the ones the wall's own clip is hiding. Not
                // composing them was measured and is worse: a drag that moves the wall then composes
                // and decodes the faces coming into view, which cost 17.5% janky frames against
                // 8.2% for keeping them all - the churn of arriving faces costs more than the
                // drawing of ones nobody sees. What is worth skipping is the *loading* of a picture
                // (see [visible]), because that is the part that is not bounded by the frame.
                key(index) {
                    ContributorAvatar(
                        contributor = contributor,
                        onScreen = index in visible,
                        targetPx = avatarPx,
                        modifier = Modifier
                            .size(avatarSize)
                            .graphicsLayer {
                                val scale = fitScale * zoom()
                                val bubble = wallBubbleTransform(
                                    geometry.centers[index] * scale + offset,
                                    viewport
                                )
                                scaleX = scale * bubble.scale
                                scaleY = scaleX
                                translationX = bubble.position.x
                                translationY = bubble.position.y
                            }
                            // No clip of its own: the face clips itself to its circle, and a second
                            // clip here is a second graphics layer per face for no visible change.
                            // The tap is detected on the face itself rather than left to a
                            // clickable: a clickable shares the wall's tap-vs-drag disambiguation,
                            // so the first tap right after the wall expands can be swallowed while
                            // that settles. detectTapGestures decides on the face directly, and the
                            // wall only claims the gesture once it is a real drag or a pinch, so a
                            // plain tap never waits on the pan. Semantics keep the face a button
                            // (role, activation, name) for accessibility and TV focus. The click
                            // label is left to Role.Button rather than onClickLabel: this Compose
                            // version has no top-level onClickLabel property, and the button role
                            // already yields a proper tap-to-activate label for the name below.
                            .semantics {
                                role = Role.Button
                                contentDescription = contributor.name
                                onClick { onSelect(contributor); true }
                            }
                            .pointerInput(contributor) {
                                detectTapGestures { onSelect(contributor) }
                            }
                    )
                }
            }
        }
    }
}

/**
 * Which faces the viewport covers, as indices into [geometry].
 *
 * A face is worth composing when its drawn centre is inside the viewport grown by the face's own
 * drawn size - the same test the draw below applies, done once per move rather than once per frame.
 */
internal fun onScreenFaces(
    geometry: ContributorWallGeometry,
    viewport: Size,
    scale: Float,
    offset: Offset,
    reach: Float
): Set<Int> {
    val onScreen = HashSet<Int>()
    geometry.centers.forEachIndexed { index, spot ->
        val position = spot * scale + offset
        if (abs(position.x) <= viewport.width / 2 + reach &&
            abs(position.y) <= viewport.height / 2 + reach
        ) {
            onScreen += index
        }
    }
    return onScreen
}

/** The size a face is drawn at, rounded up to a step, so a pinch does not re-decode per frame. */
internal fun avatarBucket(targetPx: Int): Int {
    var bucket = 32
    while (bucket < targetPx && bucket < 512) bucket *= 2
    return bucket
}

/**
 * One face: the person's picture in a ringed circle, or their initial where there is no picture.
 *
 * A face that is off screen draws its initial and loads nothing: it is not visible, and a wall of
 * well over a hundred faces cannot hold everybody's photograph at once.
 *
 * Pictures come from [ContributorAvatars], which keeps its own memory and disk cache with bounded
 * concurrency, so the loading is skipped rather than the drawing - an off-screen face stays at its
 * initial until the wall moves it into view, then fades its photograph in over the placeholder.
 */
@Composable
internal fun ContributorAvatar(
    contributor: Contributor,
    onScreen: Boolean,
    targetPx: Int,
    modifier: Modifier = Modifier
) {
    val t = LocalUIDensityTokens.current
    val context = LocalContext.current

    // The picture is keyed by URL alone, never by the on-screen size: zooming changes the pixel
    // size we ask for, and resetting on that would blank every face back to its initial for a
    // frame each time the wall is pinched. A size change re-runs the loader below and swaps the
    // bitmap in place once the sharper decode lands, leaving the current picture up meanwhile.
    var bitmap by remember(contributor.avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(contributor.avatarUrl, onScreen, targetPx) {
        // A face that leaves the screen drops its bitmap so the wall does not hold every picture;
        // one that is visible loads for the current pixel size and swaps the sharper decode in only
        // once it is ready, so a mid-zoom reload never falls back to the initial for a frame.
        if (!onScreen) {
            bitmap = null
            return@LaunchedEffect
        }
        val loaded = ContributorAvatars.load(context, contributor, targetPx)
        if (loaded != null) {
            bitmap = loaded
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = t.scale(1),
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        val picture = bitmap
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
            )
        } else {
            Text(
                text = (contributor.login ?: contributor.name).firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val AVATAR_SIZE_DP = 56
private const val AVATAR_GAP_DP = 6
private const val CANVAS_MAX_HEIGHT_DP = 380
private const val CANVAS_INSET_DP = 8
private const val MAX_VELOCITY_DP = 6000
private const val MIN_FLING_VELOCITY_DP = 50
