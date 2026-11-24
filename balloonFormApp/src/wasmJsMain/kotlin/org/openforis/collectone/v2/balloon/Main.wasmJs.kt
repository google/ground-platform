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
@file:OptIn(ExperimentalWasmJsInterop::class)

package org.openforis.collectone.v2.balloon

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@JsFun("() => window.location.search || ''") private external fun jsLocationSearch(): String

@JsFun("() => window.location.hash || ''") private external fun jsLocationHash(): String

@JsFun(
  "() => typeof window.COLLECT_ONE_USER_DATA_SERVICE_URL === 'string' " +
    "? window.COLLECT_ONE_USER_DATA_SERVICE_URL : ''"
)
private external fun jsDefaultServiceUrl(): String

@JsFun("() => window.CollectOneBalloonBridge.authPageUrl()")
private external fun jsAuthPageUrl(): String

@JsFun("(url) => window.CollectOneBalloonBridge.openExternalUrl(url)")
private external fun jsOpenExternalUrl(url: String)

@JsFun(
  "(onParamsJson) => window.CollectOneBalloonBridge.subscribe(" + "(json) => onParamsJson(json))"
)
private external fun jsSubscribeBalloonMessages(onParamsJson: (JsString) -> Unit): JsAny

@JsFun("(handle) => window.CollectOneBalloonBridge.unsubscribe(handle)")
private external fun jsUnsubscribeBalloonMessages(handle: JsAny)

@JsFun("(text) => window.CollectOneBalloonBridge.copyText(text)")
private external fun jsCopyText(text: String): Boolean

private val messageJson = Json { ignoreUnknownKeys = true }

/**
 * Entry point of the standalone KML balloon form runner (`balloonFormApp.js`).
 *
 * Runs inside sandboxed KML balloon iframes (`Origin: null`) without touching browser storage,
 * session history, or popup windows. Wires [WasmBalloonClient] into [BalloonFormViewModel] and
 * hosts the passive [BalloonFormScreen].
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val container = document.getElementById("ComposeTarget") ?: document.body!!

  ComposeViewport(container) {
    val defaultServiceUrl = remember { jsDefaultServiceUrl() }
    val authPageUrl = remember { jsAuthPageUrl() }
    var mergedParams by remember {
      mutableStateOf(parseUrlParams(jsLocationSearch()) + parseUrlParams(jsLocationHash()))
    }

    DisposableEffect(Unit) {
      val handle = jsSubscribeBalloonMessages { rawJson ->
        val incoming = parseJsonStringMap(rawJson.toString())
        if (incoming.isNotEmpty()) {
          mergedParams = mergedParams + incoming
        }
      }
      onDispose { jsUnsubscribeBalloonMessages(handle) }
    }

    val config =
      remember(mergedParams, defaultServiceUrl) {
        decodeBalloonConfig(mergedParams, defaultServiceUrl = defaultServiceUrl)
      }
    val darkTheme = remember(mergedParams) { mergedParams["theme"]?.lowercase() == "dark" }

    val viewModel = remember {
      BalloonFormViewModel(
        initialConfig = config,
        client = WasmBalloonClient(),
        authPageUrl = authPageUrl,
      )
    }
    LaunchedEffect(config) { viewModel.updateConfig(config) }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    val uiState by viewModel.uiState.collectAsState()
    BalloonFormScreen(
      uiState = uiState,
      actions = viewModel,
      effects = viewModel.effects,
      onOpenExternalUrl = { url -> jsOpenExternalUrl(url) },
      onCopyText = { text -> jsCopyText(text) },
      darkTheme = darkTheme,
    )
  }
}

private fun parseJsonStringMap(rawJson: String): Map<String, String> = runCatching {
  val obj = messageJson.parseToJsonElement(rawJson) as? JsonObject ?: return emptyMap()
  buildMap {
    obj.forEach { (key, element) ->
      runCatching { element.jsonPrimitive.content }.getOrNull()?.let { put(key, it) }
    }
  }
}
  .getOrElse { emptyMap() }
