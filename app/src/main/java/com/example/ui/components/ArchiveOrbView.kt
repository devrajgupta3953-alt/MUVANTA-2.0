package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.EnrichedCatalogItem
import com.example.model.MediaType
import com.example.model.WatchStatus
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WatchedMoss
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private data class OrbSphereNode(
    val enriched: EnrichedCatalogItem,
    val x: Float,
    val y: Float,
    val z: Float,
)

private data class ProjectedPlate(
    val enriched: EnrichedCatalogItem,
    val rect: Rect,
    val depthZ: Float,
    val scale: Float,
    val alpha: Float,
)

@Composable
fun ArchiveOrbView(
    items: List<EnrichedCatalogItem>,
    onSelectTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slice = remember(items) { items.take(48) }
    val nodes = remember(slice) {
        val count = slice.size
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        slice.mapIndexed { i, enriched ->
            val y = if (count <= 1) 0f else (1f - (i.toFloat() / (count - 1).toFloat()) * 2f)
            val r = sqrt(max(0f, 1f - y * y))
            val theta = (goldenAngle * i).toFloat()
            OrbSphereNode(
                enriched = enriched,
                x = cos(theta) * r,
                y = y,
                z = sin(theta) * r,
            )
        }
    }

    var rotY by remember { mutableFloatStateOf(0f) }
    var rotX by remember { mutableFloatStateOf(0.18f) }
    var velY by remember { mutableFloatStateOf(0.0045f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var isDragging by remember { mutableStateOf(false) }
    var focusedItem by remember { mutableStateOf<EnrichedCatalogItem?>(slice.firstOrNull()) }
    var hitBoxes by remember { mutableStateOf<List<ProjectedPlate>>(emptyList()) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastNanos == 0L) 1f else ((frameNanos - lastNanos) / 16_666_666f).coerceIn(0.2f, 2.5f)
                lastNanos = frameNanos
                if (!isDragging) {
                    rotY += velY * dt
                    rotX = (rotX + velX * dt).coerceIn(-1.15f, 1.15f)
                    velY *= 0.965f
                    velX *= 0.94f
                    if (kotlin.math.abs(velY) < 0.0025f) {
                        velY = if (velY < 0f) -0.0025f else 0.0025f
                    }
                }
            }
        }
    }

    val brassPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WarmBrass.toArgb()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val titlePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IvoryInk.toArgb()
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
    }
    val subPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SecondaryInk.toArgb()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("archive_orb_container"),
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Readout header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive 3D Archive Orb · ${slice.size} plates",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk,
                    )
                    Text(
                        text = focusedItem?.let { "#${it.item.rank} ${it.item.title} (${it.item.year})" }
                            ?: "Drag to rotate · Tap any plate to inspect",
                        style = MaterialTheme.typography.titleSmall,
                        color = WarmBrass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = { zoom = (zoom - 0.15f).coerceIn(0.65f, 1.45f) },
                        color = ArchivalSurface,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("orb_zoom_out"),
                    ) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom out orb", tint = IvoryInk, modifier = Modifier.size(16.dp))
                        }
                    }
                    Surface(
                        onClick = { zoom = (zoom + 0.15f).coerceIn(0.65f, 1.45f) },
                        color = ArchivalSurface,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("orb_zoom_in"),
                    ) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom in orb", tint = IvoryInk, modifier = Modifier.size(16.dp))
                        }
                    }
                    Surface(
                        onClick = {
                            rotX = 0.18f
                            rotY = 0f
                            zoom = 1.0f
                            velY = 0.0045f
                            velX = 0f
                        },
                        color = ArchivalSurface,
                        border = BorderStroke(1.dp, HairlineBorder),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("orb_reset"),
                    ) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset orb view", tint = IvoryInk, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 3D Projection Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .background(ObsidianBg)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dyRot = dragAmount.x * 0.0065f
                                val dxRot = -dragAmount.y * 0.0065f
                                rotY += dyRot
                                rotX = (rotX + dxRot).coerceIn(-1.15f, 1.15f)
                                velY = dyRot
                                velX = dxRot
                            },
                        )
                    }
                    .pointerInput(slice) {
                        detectTapGestures { offset ->
                            val tapped = hitBoxes
                                .asReversed()
                                .firstOrNull { plate -> plate.rect.contains(offset) }
                            if (tapped != null) {
                                focusedItem = tapped.enriched
                                onSelectTitle(tapped.enriched.item.id)
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val sphereRadius = (minOf(size.width, size.height) * 0.36f) * zoom
                    val cameraDist = sphereRadius * 3.1f
                    val basePlateW = (minOf(size.width, size.height) * 0.145f) * zoom
                    val basePlateH = basePlateW * 1.48f

                    // Subtle orbital guide rings
                    drawCircle(
                        color = HairlineBorder.copy(alpha = 0.25f),
                        radius = sphereRadius * 1.06f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawCircle(
                        color = WarmBrass.copy(alpha = 0.12f),
                        radius = sphereRadius * 0.72f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx()),
                    )

                    val cosY = cos(rotY)
                    val sinY = sin(rotY)
                    val cosX = cos(rotX)
                    val sinX = sin(rotX)

                    val projected = nodes.map { node ->
                        // Rotate around Y then X
                        val x1 = node.x * cosY - node.z * sinY
                        val z1 = node.x * sinY + node.z * cosY
                        val y2 = node.y * cosX - z1 * sinX
                        val z2 = node.y * sinX + z1 * cosX

                        val worldZ = z2 * sphereRadius
                        val perspective = cameraDist / (cameraDist - worldZ)
                        val px = cx + x1 * sphereRadius * perspective
                        val py = cy + y2 * sphereRadius * perspective
                        val w = basePlateW * perspective
                        val h = basePlateH * perspective
                        val depthNorm = ((z2 + 1f) / 2f).coerceIn(0f, 1f)
                        val alpha = (0.18f + depthNorm * 0.82f).coerceIn(0.18f, 1f)

                        ProjectedPlate(
                            enriched = node.enriched,
                            rect = Rect(px - w / 2f, py - h / 2f, px + w / 2f, py + h / 2f),
                            depthZ = z2,
                            scale = perspective,
                            alpha = alpha,
                        )
                    }.sortedBy { it.depthZ }

                    hitBoxes = projected
                    projected.lastOrNull()?.let { front ->
                        if (focusedItem?.item?.id != front.enriched.item.id) {
                            focusedItem = front.enriched
                        }
                    }

                    projected.forEach { plate ->
                        val isWatched = plate.enriched.user.status == WatchStatus.WATCHED
                        val borderCol = (if (isWatched) WatchedMoss else WarmBrass).copy(alpha = plate.alpha)
                        val fillCol = (if (plate.depthZ > 0.35f) ElevatedSurface else ArchivalSurface).copy(alpha = plate.alpha)
                        val r = plate.rect

                        drawRoundRect(
                            color = fillCol,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                        drawRoundRect(
                            color = borderCol,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = (1.1f * plate.scale).dp.toPx().coerceAtMost(2.2.dp.toPx())),
                        )

                        // Draw typographic content on foreground plates
                        if (plate.depthZ > -0.25f && r.width > 38f) {
                            drawIntoCanvas { canvas ->
                                val nc = canvas.nativeCanvas
                                val alphaInt = (plate.alpha * 255).toInt().coerceIn(30, 255)
                                val pad = r.width * 0.1f

                                brassPaint.textSize = (r.width * 0.135f).coerceIn(9f, 22f)
                                brassPaint.alpha = alphaInt
                                nc.drawText(
                                    "#${plate.enriched.item.rank}",
                                    r.left + pad,
                                    r.top + pad + brassPaint.textSize,
                                    brassPaint,
                                )

                                titlePaint.textSize = (r.width * 0.155f).coerceIn(10f, 26f)
                                titlePaint.alpha = alphaInt
                                val maxChars = ((r.width - pad * 2) / (titlePaint.textSize * 0.56f)).toInt().coerceAtLeast(6)
                                val words = plate.enriched.item.title.split(" ")
                                val lines = mutableListOf<String>()
                                var current = ""
                                for (w in words) {
                                    val candidate = if (current.isEmpty()) w else "$current $w"
                                    if (candidate.length > maxChars && current.isNotEmpty()) {
                                        lines.add(current)
                                        current = w
                                        if (lines.size >= 3) break
                                    } else {
                                        current = candidate
                                    }
                                }
                                if (current.isNotEmpty() && lines.size < 3) lines.add(current)

                                var yPos = r.top + r.height * 0.42f
                                lines.forEach { line ->
                                    val clipped = if (line.length > maxChars + 1) line.take(maxChars) + "…" else line
                                    nc.drawText(clipped, r.left + pad, yPos, titlePaint)
                                    yPos += titlePaint.textSize * 1.2f
                                }

                                subPaint.textSize = (r.width * 0.115f).coerceIn(8f, 18f)
                                subPaint.color = (if (isWatched) WatchedMoss else SecondaryInk).toArgb()
                                subPaint.alpha = alphaInt
                                val footer = if (isWatched) {
                                    "WATCHED"
                                } else {
                                    "${plate.enriched.item.year} · ${if (plate.enriched.item.type == MediaType.MOVIE) "FILM" else "TV"}"
                                }
                                nc.drawText(footer, r.left + pad, r.bottom - pad, subPaint)
                            }
                        }
                    }
                }
            }

            // Footer helper bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArchivalSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Drag sphere to orbit · Tap any poster plate to open full details",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                )
                focusedItem?.let { item ->
                    Surface(
                        onClick = { onSelectTitle(item.item.id) },
                        color = ElevatedSurface,
                        border = BorderStroke(1.dp, WarmBrass),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("orb_open_focused"),
                    ) {
                        Text(
                            text = "Open #${item.item.rank}",
                            style = MaterialTheme.typography.labelMedium,
                            color = IvoryInk,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
