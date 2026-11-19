package recipeslist

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

class TileWidthTest {
    // GridCells.Adaptive(min) fits floor(width / min) columns. Keep min in 300..360dp: below 300
    // the title column gets too narrow, above 360 a 360dp portrait phone would not fit even one
    // full tile. A 360dp phone gets exactly one column for any min above 180dp.
    @Test
    fun minTileWidthKeepsPhonesOnOneColumn() {
        assertTrue(RECIPE_TILE_MIN_WIDTH >= 300.dp, "tiles too narrow: title column is squeezed")
        assertTrue(RECIPE_TILE_MIN_WIDTH <= 360.dp, "a 360dp phone no longer fits a tile")
    }
}
