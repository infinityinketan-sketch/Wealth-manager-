package com.example.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.core.Money
import com.example.ledger.data.Category
import kotlin.math.ceil
import kotlin.math.max

/** One piece of the category donut. [category] is null for the "everything else" slice. */
data class ChartSlice(val label: String, val value: Long, val color: Color, val category: Category?, val isRest: Boolean = false)

/** A rounded-up axis maximum (1, 2, 2.5 or 5 x a power of ten) so grid labels are tidy. */
private fun niceMax(v: Long): Long {
    if (v <= 0) return 100
    var p = 1L
    while (p * 10 <= v) p *= 10
    for (m in listOf(1.0, 2.0, 2.5, 5.0, 10.0)) if (v <= (p * m).toLong()) return (p * m).toLong()
    return p * 10
}

@Composable
fun DonutChart(slices: List<ChartSlice>, total: Long, modifier: Modifier = Modifier, onSliceClick: ((ChartSlice) -> Unit)? = null) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(190.dp)) {
                val stroke = 30.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                if (total <= 0) {
                    drawArc(Color.Gray.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    return@Canvas
                }
                var start = -90f
                val gap = if (slices.size > 1) 1.5f else 0f
                for (s in slices) {
                    val sweep = 360f * s.value.toFloat() / total.toFloat()
                    if (sweep > gap) {
                        drawArc(s.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.formatCompact(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        slices.forEach { s ->
            val click = if (onSliceClick != null && !s.isRest) Modifier.clickable { onSliceClick(s) } else Modifier
            Row(modifier = click, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryBadge(s.category, size = 30.dp)
                Text(s.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pct = if (total > 0) (s.value * 100.0 / total) else 0.0
                Text(
                    String.format(java.util.Locale.ENGLISH, "%.0f%%", pct),
                    style = MaterialTheme.typography.labelLarge,
                    color = s.color,
                    fontWeight = FontWeight.Bold
                )
                Text(Money.formatInr(s.value), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp), maxLines = 1)
            }
        }
    }
}

/** Bars per day / week / month. With [income] it shows spent and income side by side. */
@Composable
fun BarChart(labels: List<String>, expense: List<Long>, income: List<Long>?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) {
            val left = 44.dp.toPx()
            val bottom = 20.dp.toPx()
            val top = 6.dp.toPx()
            val chartH = size.height - bottom - top
            val chartW = size.width - left
            val n = labels.size.coerceAtLeast(1)
            val maxV = niceMax(max(expense.maxOrNull() ?: 0L, income?.maxOrNull() ?: 0L))

            for (i in 0..3) {
                val y = top + chartH * (1 - i / 3f)
                drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val label = Money.formatCompact(maxV * i / 3)
                drawText(measurer, label, Offset(0f, y - 7.dp.toPx()), axisStyle)
            }

            val group = chartW / n
            val series = if (income != null) 2 else 1
            val barW = (group * 0.7f / series).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barW / 3, barW / 3)
            for (i in 0 until n) {
                val gx = left + group * i + (group - barW * series) / 2
                val e = expense.getOrElse(i) { 0L }
                val eh = chartH * e / maxV
                if (eh > 0) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(WealthColors.expense, Color(0xFFFF9A62)), startY = top + chartH - eh, endY = top + chartH),
                        topLeft = Offset(gx, top + chartH - eh), size = Size(barW, eh), cornerRadius = radius
                    )
                }
                if (income != null) {
                    val v = income.getOrElse(i) { 0L }
                    val ih = chartH * v / maxV
                    if (ih > 0) {
                        drawRoundRect(
                            Brush.verticalGradient(listOf(WealthColors.income, Color(0xFF2BC0E4)), startY = top + chartH - ih, endY = top + chartH),
                            topLeft = Offset(gx + barW, top + chartH - ih), size = Size(barW, ih), cornerRadius = radius
                        )
                    }
                }
            }
            val every = ceil(n / 7.0).toInt().coerceAtLeast(1)
            for (i in 0 until n step every) {
                val layout = measurer.measure(labels[i], axisStyle)
                val cx = left + group * i + group / 2 - layout.size.width / 2
                drawText(layout, topLeft = Offset(cx.coerceAtLeast(left), size.height - bottom + 4.dp.toPx()))
            }
        }
        Legend(income != null)
    }
}

/** Running total through the period: how fast money is going out (and coming in). */
@Composable
fun TrendChart(labels: List<String>, expense: List<Long>, income: List<Long>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val cumE = expense.runningFold(0L) { a, b -> a + b }.drop(1)
    val cumI = income.runningFold(0L) { a, b -> a + b }.drop(1)
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) {
            val left = 44.dp.toPx()
            val bottom = 20.dp.toPx()
            val top = 8.dp.toPx()
            val chartH = size.height - bottom - top
            val chartW = size.width - left
            val n = labels.size.coerceAtLeast(1)
            val maxV = niceMax(max(cumE.lastOrNull() ?: 0L, cumI.lastOrNull() ?: 0L))
            for (i in 0..3) {
                val y = top + chartH * (1 - i / 3f)
                drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawText(measurer, Money.formatCompact(maxV * i / 3), Offset(0f, y - 7.dp.toPx()), axisStyle)
            }
            val step = if (n > 1) chartW / (n - 1) else chartW
            fun point(i: Int, v: Long) = Offset(left + step * i, top + chartH - chartH * v / maxV)

            fun line(values: List<Long>, color: Color, fill: Boolean) {
                if (values.isEmpty()) return
                val path = Path()
                values.forEachIndexed { i, v -> val p = point(i, v); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                if (fill) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(point(values.size - 1, 0).x, top + chartH)
                        lineTo(point(0, 0).x, top + chartH)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), startY = top, endY = top + chartH))
                }
                drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(color, 4.dp.toPx(), point(values.size - 1, values.last()))
            }
            line(cumI, WealthColors.income, fill = false)
            line(cumE, WealthColors.expense, fill = true)

            val every = ceil(n / 6.0).toInt().coerceAtLeast(1)
            for (i in 0 until n step every) {
                val layout = measurer.measure(labels[i], axisStyle)
                val cx = (left + step * i - layout.size.width / 2).coerceIn(left, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(cx, size.height - bottom + 4.dp.toPx()))
            }
        }
        Legend(showIncome = true)
    }
}

/** Where the money went, by merchant, as horizontal bars in each merchant's category colour. */
@Composable
fun TopMerchants(rows: List<Triple<String, Long, Category?>>, modifier: Modifier = Modifier) {
    val maxV = rows.maxOfOrNull { it.second } ?: 0L
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.isEmpty()) {
            Text("No merchant names in this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { (name, value, category) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryBadge(category, size = 30.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.formatInr(value), fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    ) {
                        val f = if (maxV > 0) (value.toFloat() / maxV).coerceIn(0.02f, 1f) else 0f
                        Box(
                            modifier = Modifier.fillMaxWidth(f).height(8.dp)
                                .background(categoryColor(category), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(showIncome: Boolean) {
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(WealthColors.expense, "Spent")
        if (showIncome) LegendDot(WealthColors.income, "Income")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
