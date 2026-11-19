package icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Chef's hat, shown where a recipe has no photo. Drawn for this app (the source shapes are three
 * circles, a body and a band), so it carries no third-party licence.
 *
 * It is one path because `Icon` tints by alpha: the puffs, body and band are all wound clockwise
 * and simply merge, while the three pleats are wound the other way so that, under the non-zero
 * fill rule, they cancel the body and come out as see-through slits. The pleats sit below every
 * puff (y 51-60); moved higher they would overlap a circle and fill in again.
 */
val ChefHat: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChefHat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 84f,
        viewportHeight = 84f,
    ).addPath(
        pathData = addPathNodes(
            // Three puffs
            "M5,36 a17,17 0 1 1 34,0 a17,17 0 1 1 -34,0 z " +
                "M21,26 a21,21 0 1 1 42,0 a21,21 0 1 1 -42,0 z " +
                "M45,36 a17,17 0 1 1 34,0 a17,17 0 1 1 -34,0 z " +
                // Body
                "M19,37 h46 v27 h-46 z " +
                // Band
                "M22.5,68 h39 a3.5,3.5 0 0 1 3.5,3.5 v4 a3.5,3.5 0 0 1 -3.5,3.5 h-39 " +
                "a3.5,3.5 0 0 1 -3.5,-3.5 v-4 a3.5,3.5 0 0 1 3.5,-3.5 z " +
                // Pleats (counter-clockwise: holes)
                "M30.5,52.5 v6 a1.5,1.5 0 0 0 3,0 v-6 a1.5,1.5 0 0 0 -3,0 z " +
                "M40.5,52.5 v6 a1.5,1.5 0 0 0 3,0 v-6 a1.5,1.5 0 0 0 -3,0 z " +
                "M50.5,52.5 v6 a1.5,1.5 0 0 0 3,0 v-6 a1.5,1.5 0 0 0 -3,0 z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
