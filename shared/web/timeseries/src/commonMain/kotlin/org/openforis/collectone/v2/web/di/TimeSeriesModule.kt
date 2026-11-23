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
package org.openforis.collectone.v2.web.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.openforis.collectone.v2.app.client.grpc.CollectOneGrpcClient
import org.openforis.collectone.v2.app.domain.model.ImagerySource
import org.openforis.collectone.v2.web.client.timeseries.PlotImageryRepository
import org.openforis.collectone.v2.web.ui.timeseries.TimeSeriesViewModel

/**
 * The Time series panel's ViewModel. It shows scenes on the session's survey map through the
 * `(ImagerySource?) -> Unit` it is created with. Each ViewModel gets its own
 * [PlotImageryRepository], so the analyses it caches last as long as the session's panel; without a
 * backend (no gRPC client) the panel says time series are unavailable.
 */
val timeSeriesModule: Module = module {
  viewModel { parameters ->
    TimeSeriesViewModel(
      repository = getOrNull<CollectOneGrpcClient>()?.let(::PlotImageryRepository),
      showSceneOnMap = parameters.get<(ImagerySource?) -> Unit>(),
    )
  }
}
