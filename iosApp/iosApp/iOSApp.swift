// Copyright 2026 The Collect One Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import BackgroundTasks
import AppMobile
import SwiftUI
import UIKit

/// Forwards the iOS background entry points to the shared sync runtime (`AppMobileSyncIos`): silent
/// push notifications (survey change tickles) and opportunistic background refreshes.
final class AppDelegate: NSObject, UIApplicationDelegate {
  /// Must match `BGTaskSchedulerPermittedIdentifiers` in Info.plist, or submitting the request fails.
  static let refreshTaskIdentifier = "org.openforis.collectone.v2.sync.refresh"

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    // iOS requires launch handlers to be registered before the app finishes launching.
    BGTaskScheduler.shared.register(
      forTaskWithIdentifier: Self.refreshTaskIdentifier,
      using: nil
    ) { task in
      guard let refreshTask = task as? BGAppRefreshTask else {
        task.setTaskCompleted(success: false)
        return
      }
      self.handleAppRefresh(task: refreshTask)
    }
    return true
  }

  func application(
    _ application: UIApplication,
    didReceiveRemoteNotification userInfo: [AnyHashable: Any],
    fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
  ) {
    // The Kotlin side reads the push payload as strings (the same keys Android's data messages
    // carry); APNs may deliver numbers, such as a record version, as NSNumber. Other values, such as
    // the nested `aps` dictionary, aren't part of the tickle and are dropped.
    var stringMap: [String: String] = [:]
    for (key, value) in userInfo {
      if let k = key as? String {
        if let v = value as? String {
          stringMap[k] = v
        } else if let n = value as? NSNumber {
          stringMap[k] = n.stringValue
        }
      }
    }
    AppMobileSyncIos.shared.handleRemoteNotification(userInfo: stringMap) { handled in
      completionHandler(handled.boolValue ? .newData : .noData)
    }
  }

  /// Runs one background catch-up sync. The next refresh is requested first, so it stays scheduled
  /// even when iOS expires this one before the sync finishes.
  private func handleAppRefresh(task: BGAppRefreshTask) {
    scheduleNextAppRefresh()
    task.expirationHandler = {
      task.setTaskCompleted(success: false)
    }
    AppMobileSyncIos.shared.runBackgroundCatchUp { ok in
      task.setTaskCompleted(success: ok.boolValue)
    }
  }

  /// Asks iOS for a background refresh no sooner than 30 minutes from now; iOS decides when (or
  /// whether) it runs. Only `handleAppRefresh` calls this, so nothing submits the first request yet:
  /// the refresh task doesn't run until a request is also submitted elsewhere (for example when the
  /// app moves to the background).
  func scheduleNextAppRefresh() {
    let request = BGAppRefreshTaskRequest(identifier: Self.refreshTaskIdentifier)
    request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
    try? BGTaskScheduler.shared.submit(request)
  }
}

@main
struct iOSApp: App {
  @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

  init() {
    #if DEBUG
      let isDevelopmentBuild = true
    #else
      let isDevelopmentBuild = false
    #endif
    AppMobileSyncIos.shared.crashReporterBridge = IosFirebaseCrashlyticsBridge(
      isDevelopmentBuild: isDevelopmentBuild
    )
    AppMobileSyncIos.shared.configureLogging(isDevelopmentBuild: isDevelopmentBuild)
    // Lets AppMap (shared/map) render with the Mapbox Maps SDK for iOS.
    AppMapIos.shared.viewFactory = AppMapboxViewFactory()
  }

  var body: some Scene {
    WindowGroup {
      ContentView()
    }
  }
}
