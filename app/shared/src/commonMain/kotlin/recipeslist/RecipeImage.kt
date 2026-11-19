package recipeslist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import data.resolveImageUrl
import icons.ChefHat
import primary

@Composable
fun RecipeImage(imageUrl: String?, modifier: Modifier) {
    Box(modifier = Modifier) {
        if (imageUrl != null) {
            // Clipped to the same circle as the chef-hat placeholder: the background alone is
            // round, and without the clip a photo drew square over it.
            AsyncImage(
                model = resolveImageUrl(imageUrl),
                contentDescription = null,
                modifier = modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(color = Color(0xffCE5A01).copy(alpha = 0.2f)),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )
        } else {
            // Placeholder for recipes without images
            Box(
                modifier = modifier.background(
                    color = Color(0xffCE5A01).copy(alpha = 0.2f),
                    shape = CircleShape,
                ).aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ChefHat,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
