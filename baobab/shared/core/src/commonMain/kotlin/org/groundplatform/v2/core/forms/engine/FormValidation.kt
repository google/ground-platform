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
package org.groundplatform.v2.core.forms.engine

/**
 * A single problem found by [FormEngine.validate] that makes a form definition unusable.
 *
 * Reported as data rather than thrown, so form-authoring tools can list every problem alongside the
 * fields responsible instead of surfacing a stack trace.
 */
sealed interface FormValidationProblem {
  /** Human-readable description, suitable for display in authoring tools. */
  val message: String

  /** Relative field paths this problem concerns, or empty if it is not field-specific. */
  val fieldPaths: List<String>

  /**
   * The form's `calculate` / `relevant` expressions form a dependency cycle (e.g. `a = b + 1`
   * alongside `b = a + 1`), so no valid evaluation order exists.
   */
  data class CircularDependency(override val fieldPaths: List<String>) : FormValidationProblem {
    override val message: String =
      "Circular dependency between form bindings: ${fieldPaths.joinToString(", ")}. " +
        "Check the calculate and relevant expressions on these fields."
  }

  /** An XPath expression in the form definition could not be parsed. */
  data class InvalidExpression(override val message: String) : FormValidationProblem {
    // The parser reports a position within a single expression, not the owning binding, so there
    // is no field path to attribute this to yet.
    override val fieldPaths: List<String> = emptyList()
  }
}

/**
 * Outcome of [FormEngine.validate].
 *
 * @property problems every problem found, empty if the form compiles cleanly.
 */
data class FormValidationResult(val problems: List<FormValidationProblem>) {
  /** True when the form compiles cleanly and can be opened by the runtime. */
  val isValid: Boolean
    get() = problems.isEmpty()

  companion object {
    val VALID: FormValidationResult = FormValidationResult(emptyList())
  }
}
