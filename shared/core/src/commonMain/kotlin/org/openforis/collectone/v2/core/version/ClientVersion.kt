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
package org.openforis.collectone.v2.core.version

/**
 * HTTP / gRPC metadata headers shared between mobile clients and the server to communicate client
 * versions and server-side update policies.
 */
object ClientVersionHeaders {
  /** Request header identifying the client platform (`"mobile"` for mobile hosts). */
  const val CLIENT_PLATFORM = "x-client-platform"

  /** Request header carrying the client's SemVer string (e.g., `"2.0.0-SNAPSHOT"`). */
  const val CLIENT_VERSION = "x-client-version"

  /** Value of [CLIENT_PLATFORM] sent by mobile clients (Android, iOS, and mobile web). */
  const val PLATFORM_MOBILE = "mobile"

  /**
   * Response header/trailer set to `"1"` whenever the server has evaluated the mobile client
   * version policy, so clients can distinguish an empty policy from a server that does not support
   * version headers.
   */
  const val POLICY_CHECKED = "x-client-version-checked"

  /** Response header/trailer carrying the latest available mobile client version, when set. */
  const val LATEST_MOBILE_VERSION = "x-latest-mobile-version"

  /** Response header/trailer carrying the minimum required mobile client version, when set. */
  const val MIN_REQUIRED_MOBILE_VERSION = "x-min-required-mobile-version"

  /** Response header/trailer carrying the optional store or download URL for mobile updates. */
  const val MOBILE_UPDATE_URL = "x-mobile-update-url"
}

/**
 * Parsed semantic version (SemVer 2.0.0) with optional leading `v`/`V`, optional minor/patch
 * defaults (`2.1` → `2.1.0`), optional pre-release suffix (`-SNAPSHOT`, `-beta.1`), and ignored
 * `+build` metadata.
 */
data class AppVersion(
  val major: Int,
  val minor: Int = 0,
  val patch: Int = 0,
  val preRelease: String? = null,
) : Comparable<AppVersion> {

  override fun compareTo(other: AppVersion): Int =
    when {
      major != other.major -> major.compareTo(other.major)
      minor != other.minor -> minor.compareTo(other.minor)
      patch != other.patch -> patch.compareTo(other.patch)
      else -> comparePreRelease(preRelease, other.preRelease)
    }

  override fun toString(): String = buildString {
    append(major).append('.').append(minor).append('.').append(patch)
    if (!preRelease.isNullOrEmpty()) append('-').append(preRelease)
  }

  companion object {
    private val CORE_SEGMENT = Regex("^[0-9]+$")
    private val PRERELEASE_PART = Regex("^[0-9A-Za-z-]+$")

    /** Parses [raw] into an [AppVersion], or returns null if [raw] is blank or malformed. */
    fun parseOrNull(raw: String?): AppVersion? {
      val withoutBuild =
        raw?.trim().orEmpty().removePrefix("v").removePrefix("V").substringBefore('+')
      if (withoutBuild.isEmpty()) return null
      val dashIndex = withoutBuild.indexOf('-')
      val corePart = if (dashIndex >= 0) withoutBuild.substring(0, dashIndex) else withoutBuild
      val preRelease = if (dashIndex >= 0) withoutBuild.substring(dashIndex + 1) else null
      val numbers = parseCoreSegments(corePart)
      return if (numbers == null || (preRelease != null && !isValidPreRelease(preRelease))) {
        null
      } else {
        AppVersion(
          major = numbers[0],
          minor = numbers.getOrElse(1) { 0 },
          patch = numbers.getOrElse(2) { 0 },
          preRelease = preRelease,
        )
      }
    }

    private fun parseCoreSegments(corePart: String): List<Int>? {
      val segments = corePart.split('.')
      if (segments.isEmpty() || segments.size > 3) return null
      val parsed = segments.mapNotNull { segment ->
        if (CORE_SEGMENT.matches(segment)) segment.toIntOrNull() else null
      }
      return parsed.takeIf { it.size == segments.size }
    }

    private fun isValidPreRelease(preRelease: String): Boolean =
      preRelease.isNotEmpty() &&
        preRelease.split('.').all { it.isNotEmpty() && PRERELEASE_PART.matches(it) }

    /** SemVer 2.0.0 section 11.4 pre-release precedence. */
    private fun comparePreRelease(a: String?, b: String?): Int {
      if (a == null && b == null) return 0
      if (a == null) return 1
      if (b == null) return -1
      val aParts = a.split('.')
      val bParts = b.split('.')
      val common = minOf(aParts.size, bParts.size)
      for (i in 0 until common) {
        val cmp = comparePreReleasePart(aParts[i], bParts[i])
        if (cmp != 0) return cmp
      }
      return aParts.size.compareTo(bParts.size)
    }

    private fun comparePreReleasePart(a: String, b: String): Int {
      val aNum = a.toLongOrNull()
      val bNum = b.toLongOrNull()
      return when {
        aNum != null && bNum != null -> aNum.compareTo(bNum)
        aNum != null -> -1
        bNum != null -> 1
        else -> a.compareTo(b)
      }
    }
  }
}

/**
 * Server-configured version policy for mobile clients.
 *
 * @property latestVersion Latest available mobile client version; older clients receive a
 *   dismissible update suggestion. Empty disables suggestions.
 * @property minRequiredVersion Minimum required mobile client version; older clients are blocked by
 *   the server and prompted to update. Empty disables enforcement.
 * @property updateUrl Optional store or download URL opened when the user chooses to update.
 */
data class MobileClientVersionPolicy(
  val latestVersion: String = "",
  val minRequiredVersion: String = "",
  val updateUrl: String = "",
) {
  fun normalized(): MobileClientVersionPolicy =
    MobileClientVersionPolicy(
      latestVersion = latestVersion.trim(),
      minRequiredVersion = minRequiredVersion.trim(),
      updateUrl = updateUrl.trim(),
    )

  /**
   * Validates the policy fields, returning a human-readable error message or null when valid. Blank
   * fields are always valid (they disable the corresponding behavior).
   */
  fun validationError(): String? {
    val norm = normalized()
    val hasValidUrl =
      norm.updateUrl.isEmpty() ||
        norm.updateUrl.startsWith("https://") ||
        norm.updateUrl.startsWith("http://")
    return when {
      norm.latestVersion.isNotEmpty() && AppVersion.parseOrNull(norm.latestVersion) == null ->
        "Invalid latest mobile version: '${norm.latestVersion}' (expected SemVer such as 2.1.0)"
      norm.minRequiredVersion.isNotEmpty() &&
        AppVersion.parseOrNull(norm.minRequiredVersion) == null ->
        "Invalid minimum required mobile version: '${norm.minRequiredVersion}' " +
          "(expected SemVer such as 2.0.0)"
      !hasValidUrl -> "Invalid mobile update URL: must start with https:// or http://"
      else -> null
    }
  }

  /**
   * Converts this policy to response metadata headers (always includes
   * [ClientVersionHeaders.POLICY_CHECKED]).
   */
  fun toResponseHeaders(): Map<String, String> = buildMap {
    val norm = normalized()
    put(ClientVersionHeaders.POLICY_CHECKED, "1")
    if (norm.latestVersion.isNotEmpty()) {
      put(ClientVersionHeaders.LATEST_MOBILE_VERSION, norm.latestVersion)
    }
    if (norm.minRequiredVersion.isNotEmpty()) {
      put(ClientVersionHeaders.MIN_REQUIRED_MOBILE_VERSION, norm.minRequiredVersion)
    }
    if (norm.updateUrl.isNotEmpty()) {
      put(ClientVersionHeaders.MOBILE_UPDATE_URL, norm.updateUrl)
    }
  }

  companion object {
    val EMPTY = MobileClientVersionPolicy()

    /**
     * Extracts a [MobileClientVersionPolicy] from lowercased response headers/trailers, or returns
     * null if the server did not include [ClientVersionHeaders.POLICY_CHECKED] or any policy
     * header.
     */
    fun fromResponseHeaders(headers: Map<String, String>): MobileClientVersionPolicy? {
      val hasMarker = headers[ClientVersionHeaders.POLICY_CHECKED] == "1"
      val latest = headers[ClientVersionHeaders.LATEST_MOBILE_VERSION].orEmpty().trim()
      val minRequired = headers[ClientVersionHeaders.MIN_REQUIRED_MOBILE_VERSION].orEmpty().trim()
      val updateUrl = headers[ClientVersionHeaders.MOBILE_UPDATE_URL].orEmpty().trim()
      val hasPolicyValues =
        latest.isNotEmpty() || minRequired.isNotEmpty() || updateUrl.isNotEmpty()
      if (!hasMarker && !hasPolicyValues) {
        return null
      }
      return MobileClientVersionPolicy(
        latestVersion = latest,
        minRequiredVersion = minRequired,
        updateUrl = updateUrl,
      )
    }
  }
}

/** Result of evaluating a mobile client's version against a [MobileClientVersionPolicy]. */
sealed interface MobileUpdateStatus {
  /** Client meets the minimum requirement and is at or above the latest known version. */
  data object UpToDate : MobileUpdateStatus

  /** A newer mobile version is available, but the client is still allowed to run. */
  data class Suggested(
    val currentVersion: String,
    val latestVersion: String,
    val updateUrl: String = "",
  ) : MobileUpdateStatus

  /** The client is below the server's minimum required version and must update to continue. */
  data class Required(
    val currentVersion: String,
    val minRequiredVersion: String,
    val latestVersion: String = "",
    val updateUrl: String = "",
  ) : MobileUpdateStatus
}

/**
 * Evaluates [clientVersion] against [policy].
 *
 * Returns [MobileUpdateStatus.Required] when `minRequiredVersion` is set and [clientVersion] is
 * older, [MobileUpdateStatus.Suggested] when `latestVersion` is set and [clientVersion] is older,
 * and [MobileUpdateStatus.UpToDate] otherwise (or when [clientVersion] is blank/unparseable).
 */
fun evaluateMobileClientVersion(
  clientVersion: String,
  policy: MobileClientVersionPolicy,
): MobileUpdateStatus {
  val client = AppVersion.parseOrNull(clientVersion) ?: return MobileUpdateStatus.UpToDate
  val norm = policy.normalized()
  val minRequired = AppVersion.parseOrNull(norm.minRequiredVersion)
  if (minRequired != null && client < minRequired) {
    return MobileUpdateStatus.Required(
      currentVersion = clientVersion.trim(),
      minRequiredVersion = norm.minRequiredVersion,
      latestVersion = norm.latestVersion,
      updateUrl = norm.updateUrl,
    )
  }
  val latest = AppVersion.parseOrNull(norm.latestVersion)
  if (latest != null && client < latest) {
    return MobileUpdateStatus.Suggested(
      currentVersion = clientVersion.trim(),
      latestVersion = norm.latestVersion,
      updateUrl = norm.updateUrl,
    )
  }
  return MobileUpdateStatus.UpToDate
}
