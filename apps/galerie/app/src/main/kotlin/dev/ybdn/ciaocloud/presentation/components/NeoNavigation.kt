package dev.ybdn.ciaocloud.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    val palette = NeoTheme.palette
    Column(modifier = Modifier.fillMaxWidth().background(palette.page)) {
        HorizontalDivider(thickness = BorderWidth, color = palette.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val contentColor = if (selected) Ink else palette.content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (selected) {
                                Modifier.neoSurface(Lime, palette.outline, ControlRadius, shadowOffset = 0.dp)
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(ControlRadius))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item) })
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(item.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                    Text(item.label.uppercase(), style = LabelMono, color = contentColor, maxLines = 1)
                }
            }
        }
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
