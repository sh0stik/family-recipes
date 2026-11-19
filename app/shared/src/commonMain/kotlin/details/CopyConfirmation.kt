package details

/**
 * True where the system itself confirms a clipboard copy, so the app's own "Copied" banner would
 * only duplicate it. Android 13+ shows its clipboard preview at the bottom of the screen — exactly
 * where the banner sits — and covered it.
 */
expect fun systemShowsCopyConfirmation(): Boolean
