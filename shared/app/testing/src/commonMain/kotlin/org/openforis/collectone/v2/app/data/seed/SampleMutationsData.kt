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
package org.openforis.collectone.v2.app.data.seed

import org.openforis.collectone.v2.app.data.seed.backend.BackendStaticMutationsData
import org.openforis.collectone.v2.app.domain.model.MutationLogItem

/**
 * Facade delegating to [BackendStaticMutationsData] (static sample data moved out of the client and
 * into the Kotlin gRPC backend service).
 */
object SampleMutationsData {
  fun defaultMutations(): List<MutationLogItem> = BackendStaticMutationsData.defaultMutations()
}
