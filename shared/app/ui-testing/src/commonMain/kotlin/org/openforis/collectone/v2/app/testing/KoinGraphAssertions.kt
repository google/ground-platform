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
package org.openforis.collectone.v2.app.testing

import androidx.lifecycle.SavedStateHandle
import kotlin.test.assertTrue
import kotlin.test.fail
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication

/**
 * Creates every definition of the Koin graph made of [modules] (in its own Koin application), and
 * fails naming each definition that can't be created, such as one missing a dependency. ViewModels
 * get an empty [SavedStateHandle] plus [parameters], as `koinViewModel()` would pass them.
 *
 * Returns how many definitions it created.
 */
@OptIn(KoinInternalApi::class)
fun assertEveryDefinitionResolves(modules: List<Module>, vararg parameters: Any): Int {
  val koin = koinApplication { modules(modules) }.koin
  val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
  val failures = definitions.mapNotNull { definition ->
    runCatching {
      koin.get<Any>(definition.primaryType, definition.qualifier) {
        parametersOf(SavedStateHandle(), *parameters)
      }
    }
      .exceptionOrNull()
      ?.let { "$definition: $it" }
  }
  if (failures.isNotEmpty()) fail("Unresolvable Koin definitions:\n" + failures.joinToString("\n"))
  assertTrue(definitions.isNotEmpty(), "The Koin graph has no definitions")
  return definitions.size
}
