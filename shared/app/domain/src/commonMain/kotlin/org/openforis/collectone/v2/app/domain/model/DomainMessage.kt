/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openforis.collectone.v2.app.domain.model

/**
 * Text that domain code (validators, quota checks, use cases) asks the presentation layer to show,
 * as a semantic key plus format arguments. The domain holds no display strings or string resources:
 * the presentation layer (`ui/text/DomainMessageText.kt`) localizes every key and enum label.
 *
 * Arguments that are themselves a [DomainMessage] are localized first; any other argument (user
 * data such as names, IDs, or numbers) is shown as-is.
 */
sealed interface DomainMessage {

  /** The message [key] formatted with positional `%1$s`-style [args]. */
  data class Text(val key: MessageKey, val args: List<Any> = emptyList()) : DomainMessage

  /** The message [key] worded for [quantity], formatted with positional [args]. */
  data class Counted(
    val key: CountedMessageKey,
    val quantity: Int,
    val args: List<Any> = emptyList(),
  ) : DomainMessage

  /** The display label of the domain enum [value], e.g. a question type. */
  data class Label(val value: Enum<*>) : DomainMessage

  /** [message] in lowercase, for labels shown mid-sentence (e.g. a property type). */
  data class Lowercase(val message: DomainMessage) : DomainMessage
}

/** Identifies the wording of a [DomainMessage.Text]; each group of messages is an enum. */
interface MessageKey

/** Identifies the count-dependent wording of a [DomainMessage.Counted]. */
interface CountedMessageKey

/** This message formatted with [args]. */
fun MessageKey.message(vararg args: Any): DomainMessage = DomainMessage.Text(this, args.toList())

/** This message worded for [quantity] and formatted with [args]. */
fun CountedMessageKey.message(quantity: Int, vararg args: Any): DomainMessage =
  DomainMessage.Counted(this, quantity, args.toList())
