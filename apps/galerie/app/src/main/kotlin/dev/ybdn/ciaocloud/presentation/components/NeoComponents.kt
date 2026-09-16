package dev.ybdn.ciaocloud.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.presentation.theme.Brick
import dev.ybdn.ciaocloud.presentation.theme.Coral
import dev.ybdn.ciaocloud.presentation.theme.Ink
import dev.ybdn.ciaocloud.presentation.theme.LabelMono
import dev.ybdn.ciaocloud.presentation.theme.Lime
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme
import dev.ybdn.ciaocloud.presentation.theme.Sky
import dev.ybdn.ciaocloud.presentation.theme.Teal
import dev.ybdn.ciaocloud.presentation.theme.Yellow

val BorderWidth = 3.dp
val ShadowOffset = 4.dp
val CardRadius = 12.dp
val ControlRadius = 8.dp
val SmallRadius = 6.dp

/**
 * Couleur de fond d'un composant. Les tons neutres suivent le thème clair/sombre ;
 * les accents restent identiques et portent toujours du texte Ink.
 */
enum class NeoTone { Surface, Muted, Inverse, Coral, Lime, Yellow, Teal, Sky, Brick }

private val NeoTone.containerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        NeoTone.Surface -> NeoTheme.palette.surface
        NeoTone.Muted -> NeoTheme.palette.surfaceMuted
        NeoTone.Inverse -> NeoTheme.palette.content
        NeoTone.Coral -> Coral
        NeoTone.Lime -> Lime
        NeoTone.Yellow -> Yellow
        NeoTone.Teal -> Teal
        NeoTone.Sky -> Sky
        NeoTone.Brick -> Brick
    }

private val NeoTone.contentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        NeoTone.Surface, NeoTone.Muted -> NeoTheme.palette.content
        NeoTone.Inverse -> NeoTheme.palette.page
        else -> Ink
    }

/**
 * Surface néo-brutaliste : aplat de couleur, bordure et ombre dure décalée (sans flou)
 * dessinée derrière le composant.
 */
fun Modifier.neoSurface(
    color: Color,
    outline: Color,
    cornerRadius: Dp = CardRadius,
    shadowOffset: Dp = ShadowOffset,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .drawBehind {
            if (shadowOffset > 0.dp) {
                val offset = shadowOffset.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(offset, offset),
                    size = size,
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
            }
        }
        .background(color, shape)
        .border(BorderWidth, outline, shape)
}

/** Structure commune des écrans : barre de titre, en-tête optionnel, contenu défilant. */
@Composable
fun NeoScreen(
    title: String,
    header: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                NeoTopBar(title)
                header()
            }
        },
        containerColor = NeoTheme.palette.page,
        contentColor = NeoTheme.palette.content,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
fun NeoTopBar(title: String) {
    val palette = NeoTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.page)
            .statusBarsPadding(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.content,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        HorizontalDivider(thickness = BorderWidth, color = palette.outline)
    }
}

/** Bandeau défilant en capitales, motif signature du site de référence. */
@Composable
fun NeoMarquee(text: String) {
    Column(modifier = Modifier.fillMaxWidth().background(Lime)) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            maxLines = 1,
            modifier = Modifier
                .padding(vertical = 14.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 0,
                    initialDelayMillis = 0,
                    spacing = MarqueeSpacing(0.dp),
                    velocity = 40.dp,
                ),
        )
        HorizontalDivider(thickness = BorderWidth, color = NeoTheme.palette.outline)
    }
}

/**
 * Bouton à ombre dure : à l'appui, il glisse sur son ombre (effet « press-down »).
 * Désactivé, il perd son ombre et sa couleur.
 */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: NeoTone = NeoTone.Coral,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) ShadowOffset else 0.dp,
        animationSpec = tween(durationMillis = 120),
        label = "pressOffset",
    )
    val effectiveTone = if (enabled) tone else NeoTone.Muted
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .offset(pressOffset, pressOffset)
            .neoSurface(
                color = effectiveTone.containerColor,
                outline = NeoTheme.palette.outline,
                cornerRadius = ControlRadius,
                shadowOffset = if (enabled) ShadowOffset - pressOffset else 0.dp,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = effectiveTone.contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    tone: NeoTone = NeoTone.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides tone.contentColor) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .neoSurface(tone.containerColor, NeoTheme.palette.outline)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Étiquette monospace en capitales, sans ombre. */
@Composable
fun NeoTag(text: String, tone: NeoTone = NeoTone.Muted) {
    Text(
        text = text.uppercase(),
        style = LabelMono,
        color = tone.contentColor,
        modifier = Modifier
            .neoSurface(tone.containerColor, NeoTheme.palette.outline, SmallRadius, shadowOffset = 0.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Titre d'étape numérotée (« 01 », « 02 »…), comme les étapes du site. */
@Composable
fun NeoSectionHeader(index: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NeoTag(index, tone = NeoTone.Inverse)
        Text(title, style = MaterialTheme.typography.titleLarge, color = NeoTheme.palette.content)
    }
}

/** Grand chiffre accompagné d'une courte légende en minuscules. */
@Composable
fun NeoStat(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    tone: NeoTone = NeoTone.Surface,
) {
    val contentColor = tone.contentColor
    Column(
        modifier = modifier
            .neoSurface(tone.containerColor, NeoTheme.palette.outline)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = contentColor, maxLines = 1)
        Text(
            text = caption.lowercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

/** Message mis en avant : jaune pour un avertissement, corail pour une erreur. */
@Composable
fun NeoNotice(text: String, tone: NeoTone = NeoTone.Yellow) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = tone.contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .neoSurface(tone.containerColor, NeoTheme.palette.outline, ControlRadius)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Choix exclusif entre quelques options : l'option sélectionnée est « enfoncée »
 * (sans ombre, décalée sur son ombre) et colorée, les autres restent en relief.
 */
@Composable
fun NeoSegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val tone = if (isSelected) NeoTone.Lime else NeoTone.Surface
            val offset = if (isSelected) ShadowOffset else 0.dp
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .offset(offset, offset)
                    .neoSurface(
                        color = tone.containerColor,
                        outline = NeoTheme.palette.outline,
                        cornerRadius = ControlRadius,
                        shadowOffset = ShadowOffset - offset,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = tone.contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun NeoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Lime,
    height: Dp = 24.dp,
) {
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .neoSurface(NeoTheme.palette.surface, NeoTheme.palette.outline, SmallRadius, shadowOffset = 0.dp)
            .padding(BorderWidth),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(SmallRadius - BorderWidth))
                .background(color),
        )
    }
}
