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

import AppMobile
import FirebaseCore
import FirebaseCrashlytics
import Foundation

/// Swift implementation of Kotlin's `IosCrashReporterBridge` backed by Firebase Crashlytics.
///
/// Configures Firebase only when `GoogleService-Info.plist` is bundled with the app (which is
/// gitignored and supplied at build time). When absent, `isEnabled` is `false` and every method is
/// a safe no-op.
final class IosFirebaseCrashlyticsBridge: NSObject, IosCrashReporterBridge {
  let isEnabled: Bool

  init(isDevelopmentBuild: Bool) {
    let hasPlist = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
    if hasPlist && FirebaseApp.app() == nil {
      FirebaseApp.configure()
    }
    let firebaseReady = FirebaseApp.app() != nil
    let collectionEnabled = firebaseReady && !isDevelopmentBuild
    self.isEnabled = collectionEnabled
    super.init()

    if firebaseReady {
      let crashlytics = Crashlytics.crashlytics()
      crashlytics.setCrashlyticsCollectionEnabled(collectionEnabled)
      crashlytics.setCustomValue("ios", forKey: "client_platform")
      if let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
        !version.isEmpty
      {
        crashlytics.setCustomValue(version, forKey: "app_version")
      }
      if let environment = Bundle.main.infoDictionary?["APP_ENVIRONMENT"] as? String,
        !environment.isEmpty
      {
        crashlytics.setCustomValue(environment, forKey: "environment")
      }
    }
  }

  func logBreadcrumb(message: String) {
    guard isEnabled else { return }
    Crashlytics.crashlytics().log(message)
  }

  func recordException(typeName: String, message: String, stackTrace: String) {
    guard isEnabled else { return }
    let crashlytics = Crashlytics.crashlytics()
    crashlytics.log("\(typeName): \(message)")
    let userInfo: [String: Any] = [
      NSLocalizedDescriptionKey: "\(typeName): \(message)",
      "kotlin_type": typeName,
      "kotlin_stack_trace": String(stackTrace.prefix(4096)),
    ]
    let error = NSError(
      domain: "org.openforis.collectone.v2.kotlin",
      code: typeName.hashValue,
      userInfo: userInfo
    )
    crashlytics.record(error: error)
  }

  func setCustomKey(key: String, value: String) {
    guard isEnabled else { return }
    Crashlytics.crashlytics().setCustomValue(value, forKey: key)
  }

  func setUserId(userId: String?) {
    guard isEnabled else { return }
    Crashlytics.crashlytics().setUserID(userId ?? "")
  }
}
