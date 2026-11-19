package data

import androidx.compose.runtime.Composable

/** The desktop build is for development; it offers links only. */
@Composable
actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): (() -> Unit)? = null
