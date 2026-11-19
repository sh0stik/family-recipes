package details

import android.os.Build

// Android 13 (API 33) added the clipboard preview overlay that confirms every copy.
actual fun systemShowsCopyConfirmation(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
