/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppEnvironmentTest {
  @Test
  fun parse_recognizesCanonicalEnvironmentTokensAndIsNonProduction() {
    for (token in listOf("dev", "DEV", "development", " local ")) {
      assertEquals(AppEnvironment.DEV, AppEnvironment.parse(token), token)
    }
    for (token in listOf("staging", "STAGING", "stage", "preview", "demo")) {
      assertEquals(AppEnvironment.STAGING, AppEnvironment.parse(token), token)
    }
    for (token in listOf("prod", "PROD", "production", "release")) {
      assertEquals(AppEnvironment.PROD, AppEnvironment.parse(token), token)
    }
    assertNull(AppEnvironment.parse(null))
    assertNull(AppEnvironment.parse(""))
    assertNull(AppEnvironment.parse("unknown"))

    assertTrue(AppEnvironment.DEV.isNonProduction)
    assertTrue(AppEnvironment.STAGING.isNonProduction)
    assertFalse(AppEnvironment.PROD.isNonProduction)
  }

  @Test
  fun resolveWebEnvironment_explicitConfiguredEnvWinsOverHostAndDevSignIn() {
    assertEquals(
      AppEnvironment.PROD,
      resolveWebEnvironment(
        configuredEnv = "prod",
        hostname = "localhost",
        port = "8090",
        isDevSignIn = true,
      ),
    )
    assertEquals(
      AppEnvironment.STAGING,
      resolveWebEnvironment(
        configuredEnv = "staging",
        hostname = "collect.openforis.org",
        isDevSignIn = false,
      ),
    )
    assertEquals(
      AppEnvironment.DEV,
      resolveWebEnvironment(
        configuredEnv = "dev",
        hostname = "collect.openforis.org",
        isDevSignIn = false,
      ),
    )
  }

  @Test
  fun resolveWebEnvironment_classifiesLocalStagingAndProdOrigins() {
    assertEquals(
      AppEnvironment.DEV,
      resolveWebEnvironment(configuredEnv = null, hostname = "localhost", port = "8090"),
    )
    assertEquals(
      AppEnvironment.DEV,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "127.0.0.1",
        port = "4173",
        isDevSignIn = false,
      ),
    )
    assertEquals(
      AppEnvironment.DEV,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "8090-workstation.proxy.example.com",
      ),
    )
    assertEquals(
      AppEnvironment.STAGING,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "collect-one-dev--dev-tools-footer-abc123.web.app",
      ),
    )
    assertEquals(
      AppEnvironment.STAGING,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "app-demo.web.app",
      ),
    )
    assertEquals(
      AppEnvironment.STAGING,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "collect.openforis.org",
        isDevSignIn = true,
      ),
    )
    assertEquals(
      AppEnvironment.PROD,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "collect.openforis.org",
        isDevSignIn = false,
      ),
    )
    assertEquals(
      AppEnvironment.PROD,
      resolveWebEnvironment(
        configuredEnv = null,
        hostname = "collect-one.web.app",
        isDevSignIn = false,
      ),
    )
  }

  @Test
  fun resolveBackendDebugBaseUrl_handlesLocalProxyWorkstationProxyAndCloudRunUrls() {
    assertEquals(
      "http://localhost:8080",
      resolveBackendDebugBaseUrl(
        configuredDebugUrl = "http://localhost:8080/",
        configuredServiceUrl = "http://localhost:8090",
        browserProtocol = "http:",
        browserHostname = "localhost",
        browserPort = "8090",
      ),
    )
    assertEquals(
      "https://8080-dev.proxy.example.com",
      resolveBackendDebugBaseUrl(
        configuredDebugUrl = "http://localhost:8080/",
        configuredServiceUrl = "https://8090-dev.proxy.example.com",
        browserProtocol = "https:",
        browserHostname = "8090-dev.proxy.example.com",
      ),
    )
    assertEquals(
      "http://dev.workstation.example.com:8080",
      resolveBackendDebugBaseUrl(
        configuredDebugUrl = "http://localhost:8080/",
        configuredServiceUrl = "http://dev.workstation.example.com:8090",
        browserProtocol = "http:",
        browserHostname = "dev.workstation.example.com",
        browserPort = "8090",
      ),
    )
    assertEquals(
      "http://dev.workstation.example.com:8080/api",
      resolveBackendDebugBaseUrl(
        configuredDebugUrl = "http://127.0.0.1:8080/api/",
        configuredServiceUrl = "https://dev.workstation.example.com:8090",
        browserProtocol = "https:",
        browserHostname = "dev.workstation.example.com",
        browserPort = "8090",
      ),
    )
    assertEquals(
      "https://api-server-123456.europe-west4.run.app",
      resolveBackendDebugBaseUrl(
        configuredDebugUrl = null,
        configuredServiceUrl = "https://api-server-123456.europe-west4.run.app/",
        browserProtocol = "https:",
        browserHostname = "app-demo.web.app",
      ),
    )
    assertEquals(
      "",
      resolveBackendDebugBaseUrl(configuredDebugUrl = " ", configuredServiceUrl = null),
    )
  }

  @Test
  fun appBuildConfigExposesNonBlankGradleVersion() {
    assertTrue(AppBuildConfig.APP_VERSION.isNotBlank())
    assertFalse(AppBuildConfig.APP_VERSION.equals("unspecified", ignoreCase = true))
  }
}
