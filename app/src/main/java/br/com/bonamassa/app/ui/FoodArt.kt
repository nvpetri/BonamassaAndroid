package br.com.bonamassa.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.bonamassa.core.Category
import br.com.bonamassa.core.Product
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native vector-like illustration. Offline, resolution-independent; not a photo of the real menu. */
@Composable
fun FoodArt(product: Product, modifier: Modifier = Modifier, second: Product? = null) {
    Canvas(modifier.semantics { contentDescription = "Ilustração de ${product.name}" }) {
        val edge = minOf(size.width, size.height)
        val c = center
        when (product.category) {
            Category.PIZZA -> {
                val r = edge * .43f
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent), center = c, radius = edge * .5f), edge * .5f, c + Offset(0f, edge * .04f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFEED096), Color(0xFF9B4E23)), center = c, radius = r), r, c)
                drawCircle(Color(0xFFBF4825), r * .86f, c)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF3D18B), Color(0xFFDDAA55)), center = c, radius = r * .82f), r * .82f, c)
                repeat(54) { i ->
                    val angle = i * 2.399963f
                    val distance = r * .77f * kotlin.math.sqrt((i + 1f) / 55f)
                    val point = c + Offset(cos(angle) * distance, sin(angle) * distance)
                    drawCircle(Color(0xFFB36D2C).copy(alpha = .35f), r * (.016f + (i % 3) * .008f), point)
                }
                fun toppings(art: Int) {
                    repeat(13) { i ->
                        val angle = (i * 2.399963 + .3).toFloat()
                        val distance = r * .66f * kotlin.math.sqrt((i + 1f) / 14f)
                        val point = c + Offset(cos(angle) * distance, sin(angle) * distance)
                        val topping = when (art) {
                            2 -> Color(0xFFCDA475)
                            3 -> Color(0xFFD24B31)
                            5 -> Color(0xFFF9E6B2)
                            else -> Color(0xFF9F3824)
                        }
                        drawCircle(Color(0xFF824021).copy(alpha = .25f), r * .133f, point + Offset(1f, 2f))
                        drawCircle(topping, r * .124f, point)
                        drawCircle(Color(0xFFF8C77D).copy(alpha = .32f), r * .115f, point, style = Stroke(r * .012f))
                        repeat(3) { seed -> drawCircle(Color(0xFFF6D18E).copy(alpha = .7f), r * .013f,
                            point + Offset((seed - 1) * r * .045f, ((i + seed) % 2 - .5f) * r * .07f)) }
                    }
                    repeat(7) { i ->
                        val angle = i * (2 * PI / 7).toFloat()
                        val point = c + Offset(cos(angle) * r * .53f, sin(angle) * r * .53f)
                        if (art == 1 || art == 4) drawCircle(Color(0xFF5D6533), r * .044f, point, style = Stroke(r * .035f))
                        else if (art == 2 || art == 5) {
                            rotate(i * 31f, point) { drawOval(Color(0xFFFFEDC3), point - Offset(r * .055f, r * .022f), Size(r * .18f, r * .065f)) }
                        } else {
                            rotate(i * 47f, point) { drawOval(Color(0xFF456B35), point, Size(r * .17f, r * .07f)) }
                        }
                    }
                }
                if (second == null) toppings(product.art) else {
                    clipRect(right = c.x) { toppings(product.art) }
                    clipRect(left = c.x) { toppings(second.art) }
                    drawLine(Color(0xFF84572B).copy(alpha = .4f), Offset(c.x, c.y - r * .8f), Offset(c.x, c.y + r * .8f), 2f)
                }
                repeat(8) { slice ->
                    val angle = slice * (PI / 4).toFloat() + .2f
                    drawLine(Color(0xFF866538).copy(alpha = .2f), c, c + Offset(cos(angle) * r * .8f, sin(angle) * r * .8f), 1.5f)
                }
            }
            Category.DRINK -> {
                val w = edge * .29f; val h = edge * .65f
                val tint = when (product.art) { 7 -> Color(0xFF455E35); 8 -> Color(0xFF7595A1); else -> Color(0xFF442C25) }
                drawOval(Color.Black.copy(alpha = .25f), Offset(c.x - w, c.y + h * .47f), Size(w * 2f, h * .12f))
                drawRoundRect(tint, Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(w * .25f))
                drawRoundRect(tint, Offset(c.x - w * .22f, c.y - h * .65f), Size(w * .44f, h * .2f), CornerRadius(w * .1f))
                drawRoundRect(if (product.art == 8) Brand.Cream else Brand.Red, Offset(c.x - w * .24f, c.y - h * .69f), Size(w * .48f, h * .07f), CornerRadius(2f))
                drawRect(if (product.art == 7) Brand.Gold else Brand.Red, Offset(c.x - w / 2, c.y - h * .12f), Size(w, h * .27f))
                drawLine(Color.White.copy(alpha = .16f), Offset(c.x - w * .3f, c.y - h * .36f), Offset(c.x - w * .3f, c.y + h * .39f), w * .1f)
            }
            Category.DESSERT -> {
                drawOval(Color.Black.copy(alpha = .3f), Offset(c.x - edge * .38f, c.y + edge * .13f), Size(edge * .76f, edge * .23f))
                rotate(-12f) {
                    drawRoundRect(Color(0xFF48281E), c - Offset(edge * .3f, edge * .22f), Size(edge * .6f, edge * .5f), CornerRadius(edge * .05f))
                    drawRoundRect(Color(0xFF784331), c - Offset(edge * .3f, edge * .22f), Size(edge * .6f, edge * .37f), CornerRadius(edge * .05f))
                    repeat(18) { i -> drawCircle(Color(0xFFC39A72), edge * .012f, c + Offset(((i * 17 % 10) - 5) * edge * .046f, ((i * 13 % 7) - 4) * edge * .041f)) }
                }
            }
        }
    }
}
