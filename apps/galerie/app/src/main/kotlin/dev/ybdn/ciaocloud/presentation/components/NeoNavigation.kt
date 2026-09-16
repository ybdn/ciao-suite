package dev.ybdn.ciaocloud.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.presentation.theme.Ink
import dev.ybdn.ciaocloud.presentation.theme.LabelMono
import dev.ybdn.ciaocloud.presentation.theme.Lime
import dev.ybdn.ciaocloud.presentation.theme.NeoTheme

data class NeoNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Barre de navigation basse : l'onglet actif est un aplat citron bordé, les autres restent nus. */
@Composable
fun NeoBottomBar(
    items: List<NeoNavItem>,
    selectedRoute: String?,
    onSelect: (NeoNavItem) -> Unit,
) {
    NeoBarContainer {
        items.forEach { item ->
            NeoBarItem(
                icon = item.icon,
                label = item.label,
                highlighted = item.route == selectedRoute,
                role = Role.Tab,
                onClick = { onSelect(item) },
            )
        }
    }
}

data class NeoAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    /** État actif (ex. favori) : même aplat citron que l'onglet sélectionné. */
    val highlighted: Boolean = false,
)

/** Barre d'actions basse, aux mêmes proportions que [NeoBottomBar]. */
@Composable
fun NeoActionBar(actions: List<NeoAction>, modifier: Modifier = Modifier) {
    NeoBarContainer(modifier) {
        actions.forEach { action ->
            NeoBarItem(
                icon = action.icon,
                label = action.label,
                highlighted = action.highlighted,
                role = Role.Button,
                enabled = action.enabled,
                onClick = action.onClick,
            )
        }
    }
}

@Composable
private fun NeoBarContainer(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val palette = NeoTheme.palette
    Column(modifier = modifier.fillMaxWidth().background(palette.page)) {
        HorizontalDivider(thickness = BorderWidth, color = palette.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun RowScope.NeoBarItem(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    role: Role,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val palette = NeoTheme.palette
    val contentColor = if (highlighted) Ink else palette.content
    Column(
        modifier = Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (highlighted) {
                    Modifier.neoSurface(Lime, palette.outline, ControlRadius, shadowOffset = 0.dp)
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(ControlRadius))
            .selectable(selected = highlighted, enabled = enabled, role = role, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Text(label.uppercase(), style = LabelMono, color = contentColor, maxLines = 1)
    }
}

/** Rangée de filtres défilante ; le filtre actif est « enfoncé » et coloré, comme `NeoSegmentedChoice`. */
@Composable
fun NeoChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val offset = if (selected) ShadowOffset / 2 else 0.dp
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Ink else NeoTheme.palette.content,
                maxLines = 1,
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .offset(offset, offset)
                    .neoSurface(
                        color = if (selected) Lime else NeoTheme.palette.surface,
                        outline = NeoTheme.palette.outline,
                        cornerRadius = ControlRadius,
                        shadowOffset = ShadowOffset / 2 - offset,
                    )
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}
