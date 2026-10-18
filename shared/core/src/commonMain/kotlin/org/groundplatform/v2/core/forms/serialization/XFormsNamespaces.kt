/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.core.forms.serialization

/** W3C XForms namespace, the default namespace of an XForms document's model and body. */
const val XFORMS_NAMESPACE = "http://www.w3.org/2002/xforms"

/** XHTML namespace (conventional prefix `h`), used for the `<h:html>` document wrapper. */
const val XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml"

/** JavaRosa namespace (conventional prefix `jr`), e.g. `jr:constraintMsg`, `jr:preload`. */
const val JAVAROSA_NAMESPACE = "http://openrosa.org/javarosa"

/** OpenRosa XForms namespace (conventional prefix `orx`), e.g. `orx:max-pixels`, `orx:meta`. */
const val OPENROSA_XFORMS_NAMESPACE = "http://openrosa.org/xforms"

/** ODK XForms namespace (conventional prefix `odk`), e.g. `odk:xforms-version`. */
const val ODK_XFORMS_NAMESPACE = "http://www.opendatakit.org/xforms"

/** ODK Entities namespace (conventional prefix `entities`), e.g. `entities:saveto`. */
const val ODK_ENTITIES_NAMESPACE = "http://www.opendatakit.org/xforms/entities"

/** XML Events namespace (conventional prefix `ev`). */
const val XML_EVENTS_NAMESPACE = "http://www.w3.org/2001/xml-events"

/** XML Schema namespace (conventional prefix `xsd`). */
const val XML_SCHEMA_NAMESPACE = "http://www.w3.org/2001/XMLSchema"

/**
 * Ground XForms extension namespace (conventional prefix [GROUND_XFORMS_PREFIX]), e.g.
 * `ground:concept="eudr.commodity@1"` on a `<bind>`.
 *
 * ProtoForms doesn't model this namespace: its attributes are carried as
 * `FieldBinding.foreign_attributes` like any other tool's extension (see
 * `docs/technical/model/library/01-concepts.md`, "XForms and XLSForm Serialization").
 */
const val GROUND_XFORMS_NAMESPACE = "http://groundplatform.org/xforms"

/** Conventional prefix for [GROUND_XFORMS_NAMESPACE]. */
const val GROUND_XFORMS_PREFIX = "ground"

/**
 * Namespace bound to the reserved `xml` prefix. Namespaces in XML 1.0 (Third Edition), section 3:
 * the prefix is bound by definition and needn't (and normally isn't) declared.
 */
internal const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"

/**
 * Namespaces whose attributes ProtoForms models (or deliberately ignores) on `<bind>`. Attributes
 * in any other namespace are preserved as `FieldBinding.foreign_attributes`.
 */
internal val MODELED_XFORMS_NAMESPACES: Set<String> =
  setOf(
    XFORMS_NAMESPACE,
    XHTML_NAMESPACE,
    JAVAROSA_NAMESPACE,
    OPENROSA_XFORMS_NAMESPACE,
    ODK_XFORMS_NAMESPACE,
    ODK_ENTITIES_NAMESPACE,
    XML_EVENTS_NAMESPACE,
    XML_SCHEMA_NAMESPACE,
  )

/**
 * Conventional prefixes of [MODELED_XFORMS_NAMESPACES]. Attributes using one of these prefixes
 * without an in-scope declaration (common in hand-written fixtures) are treated as modeled rather
 * than foreign.
 */
internal val CONVENTIONAL_XFORMS_PREFIXES: Map<String, String> =
  mapOf(
    "h" to XHTML_NAMESPACE,
    "jr" to JAVAROSA_NAMESPACE,
    "orx" to OPENROSA_XFORMS_NAMESPACE,
    "odk" to ODK_XFORMS_NAMESPACE,
    "entities" to ODK_ENTITIES_NAMESPACE,
    "ev" to XML_EVENTS_NAMESPACE,
    "xsd" to XML_SCHEMA_NAMESPACE,
  )
