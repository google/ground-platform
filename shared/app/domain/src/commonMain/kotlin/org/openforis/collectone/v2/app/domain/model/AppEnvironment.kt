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

/**
 * Runtime deployment tier of a Collect One client build.
 *
 * Non-production builds ([DEV] and [STAGING]) surface developer-facing affordances such as the demo
 * attribution footer and the web Developer Tools dialog; [PROD] hides them.
 */
enum class AppEnvironment {
  DEV,
  STAGING,
  PROD;

  /** True in [DEV] and [STAGING]; false in [PROD]. */
  val isNonProduction: Boolean
    get() = this != PROD

  companion object {
    /** Parses an explicit `APP_ENV` / `COLLECT_ONE_ENV` token, or returns null when unset. */
    fun parse(raw: String?): AppEnvironment? =
      when (raw?.trim()?.lowercase()) {
        "dev",
        "development",
        "local" -> DEV
        "staging",
        "stage",
        "preview",
        "demo" -> STAGING
        "prod",
        "production",
        "release" -> PROD
        else -> null
      }
  }
}

private val PORT_PREFIXED_PROXY_HOST = Regex("""^(\d+)-(.*\..*)$""")
private val LOCAL_BACKEND_ORIGIN =
  Regex("""^(https?)://(?:localhost|127\.0\.0\.1|\[::1]):(\d+)(/.*)?$""", RegexOption.IGNORE_CASE)
private val LOCAL_LOOPBACK_HOSTS = setOf("", "localhost", "127.0.0.1", "::1", "[::1]")
private val STANDARD_WEB_PORTS = setOf("80", "443")

/**
 * Resolves the active [AppEnvironment] for a browser host.
 *
 * An explicit [configuredEnv] (`window.COLLECT_ONE_ENV` from `APP_ENV`) always wins. Otherwise:
 * - Local hosts (`localhost`, `127.0.0.1`, `[::1]`, workstation proxy hosts, or custom local ports)
 *   resolve to [AppEnvironment.DEV].
 * - Firebase Hosting preview channels (`*--*.web.app`), `*-dev` / `*-demo` / `staging` hosts, and
 *   deployed bundles with [isDevSignIn] enabled resolve to [AppEnvironment.STAGING].
 * - All other deployed origins resolve to [AppEnvironment.PROD].
 */
fun resolveWebEnvironment(
  configuredEnv: String?,
  hostname: String,
  port: String = "",
  isDevSignIn: Boolean = false,
): AppEnvironment {
  val explicit = AppEnvironment.parse(configuredEnv)
  if (explicit != null) return explicit
  val host = hostname.trim().lowercase()
  val normalizedPort = port.trim()
  return when {
    isLocalBrowserHost(host, normalizedPort) -> AppEnvironment.DEV
    isStagingBrowserHost(host) || isDevSignIn -> AppEnvironment.STAGING
    else -> AppEnvironment.PROD
  }
}

private fun isLocalBrowserHost(host: String, port: String): Boolean {
  val isLoopbackOrProxy =
    host in LOCAL_LOOPBACK_HOSTS || host.endsWith(".localhost") || host.contains(".proxy.")
  val hasDevPort = port.isNotEmpty() && port !in STANDARD_WEB_PORTS
  return isLoopbackOrProxy || hasDevPort
}

private fun isStagingBrowserHost(host: String): Boolean {
  val isFirebaseDomain = host.endsWith(".web.app") || host.endsWith(".firebaseapp.com")
  val isPreviewChannel = isFirebaseDomain && "--" in host
  val isDemoHost =
    host.endsWith("-dev.web.app") ||
      host.endsWith("-dev.firebaseapp.com") ||
      host.endsWith("-demo.web.app") ||
      host.endsWith("-demo.firebaseapp.com")
  val isStagingSubdomain =
    host.startsWith("staging.") || host.contains("-staging.") || host.contains(".staging.")
  return isPreviewChannel || isDemoHost || isStagingSubdomain
}

/**
 * Resolves the base URL of the backend server (`server/`) for opening `/debug*` consoles, without a
 * trailing slash, or `""` when no backend URL is configured.
 *
 * When a local webpack dev server proxies gRPC-Web over the page's own origin, [configuredDebugUrl]
 * carries the upstream `USER_DATA_SERVICE_URL` (e.g. `http://localhost:8080`). When the browser
 * reached that dev server through a port-prefixed workstation proxy (`8090-host.proxy.example.com`)
 * or a remote workstation hostname (`host.example.com:8090`), a `localhost:<port>` backend URL is
 * rewritten to the matching `<port>-host.proxy.example.com` or `host.example.com:<port>` origin so
 * debug console links open in the developer's browser.
 */
fun resolveBackendDebugBaseUrl(
  configuredDebugUrl: String?,
  configuredServiceUrl: String?,
  browserProtocol: String = "",
  browserHostname: String = "",
  browserPort: String = "",
): String {
  val raw =
    configuredDebugUrl?.trim()?.takeIf { it.isNotEmpty() }
      ?: configuredServiceUrl?.trim()?.takeIf { it.isNotEmpty() }
      ?: return ""
  val trimmed = raw.trimEnd('/')
  val host = browserHostname.trim()
  val localBackendMatch = LOCAL_BACKEND_ORIGIN.matchEntire(trimmed)
  if (localBackendMatch != null && host.lowercase() !in LOCAL_LOOPBACK_HOSTS) {
    val backendScheme = localBackendMatch.groupValues[1].lowercase()
    val targetPort = localBackendMatch.groupValues[2]
    val pathSuffix = localBackendMatch.groupValues[3].trimEnd('/')
    val proxyMatch = PORT_PREFIXED_PROXY_HOST.matchEntire(host)
    if (proxyMatch != null) {
      val proxyDomain = proxyMatch.groupValues[2]
      val scheme = browserProtocol.trim().ifEmpty { "https:" }
      val normalizedScheme = if (scheme.endsWith(":")) scheme else "$scheme:"
      val portPart = browserPort.trim().let { if (it.isNotEmpty()) ":$it" else "" }
      return "$normalizedScheme//$targetPort-$proxyDomain$portPart$pathSuffix"
    }
    val formattedHost = if (':' in host && !host.startsWith('[')) "[$host]" else host
    return "$backendScheme://$formattedHost:$targetPort$pathSuffix"
  }
  return trimmed
}
