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
package org.openforis.collectone.v2.web.ui.text

import org.jetbrains.compose.resources.StringResource
import org.openforis.collectone.v2.web.domain.model.OrganizationRelation
import org.openforis.collectone.v2.web.resources.Res
import org.openforis.collectone.v2.web.resources.org_relation_invited
import org.openforis.collectone.v2.web.resources.org_relation_manager
import org.openforis.collectone.v2.web.resources.org_relation_member
import org.openforis.collectone.v2.web.resources.org_relation_requested

/** The organizations directory's badge text, or `null` when there's no relation to show. */
val OrganizationRelation.labelRes: StringResource?
  get() =
    when (this) {
      OrganizationRelation.MANAGER -> Res.string.org_relation_manager
      OrganizationRelation.MEMBER -> Res.string.org_relation_member
      OrganizationRelation.INVITED -> Res.string.org_relation_invited
      OrganizationRelation.REQUESTED -> Res.string.org_relation_requested
      OrganizationRelation.NONE -> null
    }
