package recipeslist

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import honey
import model.Recipe
import primary
import sugar

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeListItem(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    recipe: Recipe,
    onClick: (recipe: Recipe) -> Unit,
) {
    Box(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                clip = true,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.15f),
            )
            .clickable { onClick(recipe) }
    ) {
        with(sharedTransitionScope) {
            Card(
                backgroundColor = sugar,
                shape = RoundedCornerShape(16.dp),
                elevation = 0.dp,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).sharedElement(
                    state = rememberSharedContentState(key = "item-container-${recipe.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Image
                    RecipeListItemImageWrapper(
                        modifier = Modifier.width(80.dp).aspectRatio(1f),
                        child = {
                            RecipeImage(
                                imageUrl = recipe.photoUrls.firstOrNull(),
                                modifier = Modifier.sharedElement(
                                    state = rememberSharedContentState(key = "item-image-${recipe.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.subtitle1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedElement(
                                state = rememberSharedContentState(key = "item-title-${recipe.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        )

                        if (recipe.description.isNotBlank()) {
                            Text(
                                text = recipe.description,
                                style = MaterialTheme.typography.body2,
                                color = Color.Gray,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp).sharedElement(
                                    state = rememberSharedContentState(key = "recipe-description-${recipe.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            )
                        }

                        // Category chips
                        if (recipe.categories.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                recipe.categories.take(3).forEach { category ->
                                    Box(
                                        modifier = Modifier
                                            .background(honey.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.caption,
                                            color = honey,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (recipe.categories.size > 3) {
                                    Text(
                                        text = "+${recipe.categories.size - 3}",
                                        style = MaterialTheme.typography.caption,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(start = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
