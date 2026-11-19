import UIKit
import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        Main_iosKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea()
                // Recipe links (shared/navigation/DeepLinks.kt). A universal link normally
                // arrives as a browsing-web activity; onOpenURL covers the cases where SwiftUI
                // hands it over as a plain URL instead. Either way the Kotlin side opens it.
                .onOpenURL { url in
                    _ = DeepLinks.shared.handleUrl(url: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        _ = DeepLinks.shared.handleUrl(url: url.absoluteString)
                    }
                }
    }
}
