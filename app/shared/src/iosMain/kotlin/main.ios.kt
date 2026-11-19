import androidx.compose.ui.window.ComposeUIViewController
import data.TokenStorage

fun MainViewController() =
    ComposeUIViewController {
        // No SensorManager on iOS — deliberately null.
        //
        // The tilt/parallax effect on the recipe detail screen came from the JetBrains KMP
        // template and was driven by CMMotionManager. Any app that touches CoreMotion must
        // declare NSMotionUsageDescription in Info.plist; without that purpose string iOS
        // terminates the process on first motion access. That crash is invisible in the
        // Simulator (isGyroAvailable() returns false there) but fires on real hardware as
        // soon as a recipe is opened.
        //
        // App() takes a nullable SensorManager and the detail screens null-check before
        // registering, so passing null simply skips the cosmetic effect. Android keeps its
        // own implementation in androidMain and is unaffected.
        App(sensorManager = null, tokenStorage = TokenStorage(), enableSwipeBack = true)
    }
