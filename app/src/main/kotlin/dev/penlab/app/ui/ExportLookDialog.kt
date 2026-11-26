package dev.penlab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.penlab.app.ink.InkColors
import dev.penlab.app.ink.InkTools
import dev.penlab.app.ink.PageBackground
import dev.penlab.app.ink.PageLayout
import dev.penlab.app.ink.PageStyle
import dev.penlab.app.ui.components.PenLabDialog
import dev.penlab.app.ui.theme.LocalPenLabColors
import dev.penlab.app.ui.theme.PenLabComponentTokens
import dev.penlab.app.ui.theme.PenLabLayout
import dev.penlab.app.ui.theme.PenLabShapes
import dev.penlab.app.ui.theme.PenLabSpacing

/**
 * Asked before exporting a dark-page note: crisp white paper with dark ink (for printing or
 * handing in), or the dark page as it appears on screen. PDF notebooks stay white and skip this.
 */
@Composable
fun ExportLookDialog(
  onPick: (dark: Boolean) -> Unit,
  onDismiss: () -> Unit,
  /** The notebook's own paper, so the previews show what will be exported (not always lined paper). */
  paper: ExportPaper = ExportPaper(),
) {
  var selectedDark by remember { mutableStateOf(false) }
  val scheme = MaterialTheme.colorScheme

  PenLabDialog(
    title = "Export PDF",
    subtitle = "Choose how this dark-page notebook should look in the exported PDF.",
    onDismiss = onDismiss,
    primaryLabel = "Export PDF",
    onPrimaryClick = { onPick(selectedDark) },
    secondaryLabel = "Cancel",
    onSecondaryClick = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.md),
    ) {
      ExportLookCard(
        title = "Light paper",
        description = "White paper, dark ink · best for printing",
        icon = PenLabIcons.Sun,
        darkPreview = false,
        paper = paper,
        isSelected = !selectedDark,
        onClick = { selectedDark = false },
        modifier = Modifier.weight(1f),
      )
      ExportLookCard(
        title = "Dark page",
        description = "Dark backdrop, light ink · as on screen",
        icon = PenLabIcons.Moon,
        darkPreview = true,
        paper = paper,
        isSelected = selectedDark,
        onClick = { selectedDark = true },
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(PenLabShapes.small)
          .background(PenLabComponentTokens.chipIdleBg)
          .padding(horizontal = PenLabSpacing.md, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
    ) {
      Icon(
        imageVector = PenLabIcons.Pdf,
        contentDescription = null,
        tint = scheme.primary,
        modifier = Modifier.size(PenLabLayout.iconCompact),
      )
      Text(
        text = "Vector ink is embedded cleanly in the exported PDF at full resolution.",
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun ExportLookCard(
  title: String,
  description: String,
  icon: ImageVector,
  darkPreview: Boolean,
  paper: ExportPaper,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val bg = if (isSelected) PenLabComponentTokens.chipSelectedBg else PenLabComponentTokens.chipIdleBg
  val borderColor = if (isSelected) scheme.primary else scheme.outlineVariant
  val borderWidth = if (isSelected) 1.5.dp else 1.dp
  val titleColor = if (isSelected) PenLabComponentTokens.chipSelectedContent else scheme.onSurface
  val descColor =
    if (isSelected) PenLabComponentTokens.chipSelectedContent.copy(alpha = 0.82f)
    else scheme.onSurfaceVariant

  Column(
    modifier =
      modifier
        .clip(PenLabShapes.medium)
        .background(bg)
        .border(borderWidth, borderColor, PenLabShapes.medium)
        .selectable(
          selected = isSelected,
          role = Role.RadioButton,
          onClick = onClick,
        )
        .padding(PenLabSpacing.md)
        .semantics {
          contentDescription = "$title. $description"
          if (isSelected) selected = true
        },
    verticalArrangement = Arrangement.spacedBy(PenLabSpacing.sm),
  ) {
    ExportPagePreview(darkPage = darkPreview, paper = paper)

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
        modifier = Modifier.size(PenLabLayout.iconCompact),
      )
      Text(
        text = title,
        color = titleColor,
        style =
          MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
          ),
        modifier = Modifier.weight(1f),
        maxLines = 1,
      )
      if (isSelected) {
        Box(
          modifier = Modifier.size(20.dp).clip(CircleShape).background(scheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = PenLabIcons.CheckBare,
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier.size(12.dp),
          )
        }
      } else {
        Spacer(Modifier.size(20.dp))
      }
    }

    Text(
      text = description,
      color = descColor,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

/** The notebook's paper as the export-look previews show it. */
data class ExportPaper(
  val style: PageStyle = PageStyle.LINED,
  val widthMm: Float = 210f,
  val heightMm: Float = 297f,
  val rulingMm: Float = 0f,
)

/** Miniature vector-ink page preview rendered strictly from PenLab's paper and ink color model. */
@Composable
private fun ExportPagePreview(darkPage: Boolean, paper: ExportPaper) {
  val ruling =
    remember(darkPage, paper) {
      PageBackground(PageLayout.UNITS_PER_MM * 25.4f / 160f, darkPage).apply {
        pageWidth = paper.widthMm * PageLayout.UNITS_PER_MM
        pageHeight = paper.heightMm * PageLayout.UNITS_PER_MM
        spacingMm = paper.rulingMm
      }
    }
  val paperColor = Color(InkColors.pageOnScreen(darkPage))
  val primaryInk = Color(InkColors.onScreen(InkTools.penColors[0].paperColor, darkPage) or 0xFF000000.toInt())
  val accentInk = Color(InkColors.onScreen(InkTools.penColors[1].paperColor, darkPage) or 0xFF000000.toInt())
  val edgeColor = LocalPenLabColors.current.swatchEdge
  val shape = PenLabShapes.small

  Canvas(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(78.dp)
        .clip(shape)
        .background(paperColor)
        .border(1.dp, edgeColor, shape),
  ) {
    // The notebook's own paper (lined, grid, dotted, Cornell...), the top of the page across its full
    // width, drawn by the same code that draws the page and the export.
    drawIntoCanvas { canvas ->
      val native = canvas.nativeCanvas
      val scale = size.width / (paper.widthMm * PageLayout.UNITS_PER_MM)
      native.save()
      native.scale(scale, scale)
      ruling.draw(native, paper.style, android.graphics.RectF(0f, 0f, size.width / scale, size.height / scale), scale)
      native.restore()
    }

    // Primary handwritten-style vector curve (equation line)
    val mainStroke =
      Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.42f)
        cubicTo(
          size.width * 0.28f, size.height * 0.22f,
          size.width * 0.42f, size.height * 0.56f,
          size.width * 0.58f, size.height * 0.34f,
        )
        cubicTo(
          size.width * 0.68f, size.height * 0.22f,
          size.width * 0.78f, size.height * 0.46f,
          size.width * 0.88f, size.height * 0.36f,
        )
      }
    drawPath(
      path = mainStroke,
      color = primaryInk,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Secondary annotation curve (theorem / underline)
    val noteStroke =
      Path().apply {
        moveTo(size.width * 0.16f, size.height * 0.70f)
        cubicTo(
          size.width * 0.34f, size.height * 0.60f,
          size.width * 0.50f, size.height * 0.78f,
          size.width * 0.70f, size.height * 0.66f,
        )
      }
    drawPath(
      path = noteStroke,
      color = accentInk,
      style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}
