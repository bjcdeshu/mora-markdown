package de.unbow.mora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.unbow.mora.R
import de.unbow.mora.markdown.ReaderFontFamily
import de.unbow.mora.markdown.ReaderSurfaceTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(
    fontSize: Float,
    lineHeight: Float,
    horizontalPadding: Float,
    fontFamily: ReaderFontFamily,
    surfaceTone: ReaderSurfaceTone,
    onFontSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onHorizontalPaddingChanged: (Float) -> Unit,
    onFontFamilyChanged: (ReaderFontFamily) -> Unit,
    onSurfaceToneChanged: (ReaderSurfaceTone) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.reading_typography),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.reading_font_family),
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    selected = fontFamily == ReaderFontFamily.SANS,
                    onClick = { onFontFamilyChanged(ReaderFontFamily.SANS) },
                    label = {
                        Text(
                            text = stringResource(R.string.font_family_sans),
                            fontFamily = FontFamily.SansSerif,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = fontFamily == ReaderFontFamily.SERIF,
                    onClick = { onFontFamilyChanged(ReaderFontFamily.SERIF) },
                    label = {
                        Text(
                            text = stringResource(R.string.font_family_serif),
                            fontFamily = FontFamily.Serif,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.reading_surface_tone),
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    selected = surfaceTone == ReaderSurfaceTone.DEFAULT,
                    onClick = { onSurfaceToneChanged(ReaderSurfaceTone.DEFAULT) },
                    label = { Text(stringResource(R.string.surface_tone_default)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = surfaceTone == ReaderSurfaceTone.WARM_PAPER,
                    onClick = { onSurfaceToneChanged(ReaderSurfaceTone.WARM_PAPER) },
                    label = { Text(stringResource(R.string.surface_tone_warm_paper)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingSlider(
                title = stringResource(R.string.body_font_size),
                valueLabel = stringResource(R.string.sp_value, fontSize.toInt()),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
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
