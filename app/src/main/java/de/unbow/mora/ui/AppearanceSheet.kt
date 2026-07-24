package de.unbow.mora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.unbow.mora.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(
    fontSize: Float,
    lineHeight: Float,
    horizontalPadding: Float,
    onFontSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onHorizontalPaddingChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.reading_typography),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
            SettingSlider(
                title = stringResource(R.string.body_font_size),
                valueLabel = stringResource(R.string.pixels_value, fontSize.toInt()),
                value = fontSize,
                range = 15f..21f,
                steps = 5,
                onValueChange = onFontSizeChanged,
            )
            SettingSlider(
                title = stringResource(R.string.line_height),
                valueLabel = stringResource(R.string.decimal_value, lineHeight),
                value = lineHeight,
                range = 1.5f..2.1f,
                steps = 11,
                onValueChange = onLineHeightChanged,
            )
            SettingSlider(
                title = stringResource(R.string.page_margins),
                valueLabel = stringResource(R.string.pixels_value, horizontalPadding.toInt()),
                value = horizontalPadding,
                range = 16f..34f,
                steps = 8,
                onValueChange = onHorizontalPaddingChanged,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.restore_defaults))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            fontWeight = FontWeight.Medium,
        )
        Text(
            valueLabel,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
    )
    Spacer(Modifier.height(10.dp))
}
