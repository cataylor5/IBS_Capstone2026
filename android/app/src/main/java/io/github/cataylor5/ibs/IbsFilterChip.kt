package io.github.cataylor5.ibs

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.cataylor5.ibs.ui.theme.IbsBrand

@Composable
internal fun IbsFilterChip(selected: Boolean, onClick: () -> Unit,
    label: @Composable () -> Unit, enabled: Boolean = true) {
    FilterChip(selected, onClick, label, enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer),
        border = BorderStroke(1.dp, if (selected && enabled) androidx.compose.ui.graphics.Color(io.github.cataylor5.ibs.ui.theme.IbsAppearance.palette.actionText)
            else MaterialTheme.colorScheme.outlineVariant))
}
