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
package org.openforis.collectone.v2.core.forms.xpath.functions

import org.openforis.collectone.v2.core.forms.xpath.XPathEvaluationException
import org.openforis.collectone.v2.core.forms.xpath.model.XPathNode
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** XPath 1.0 node-set functions (section 4.1) and the ODK node-set extensions. */
internal object NodeSetFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("count") {
      checkEagerArgCount(name, args, 1..1)
      val arg = args[0]
      if (arg !is XPathValue.NodeSet) {
        throw XPathEvaluationException("Function 'count' requires a node-set argument")
      }
      XPathValue.Number(arg.nodes.size.toDouble())
    }
    define("count-non-empty") {
      checkEagerArgCount(name, args, 1..1)
      val arg = args[0]
      if (arg !is XPathValue.NodeSet) {
        throw XPathEvaluationException("Function 'count-non-empty' requires a node-set argument")
      }
      val count = arg.nodes.count { !it.extractValue().isEmptyValue() }
      XPathValue.Number(count.toDouble())
    }
    define("position") {
      checkEagerArgCount(name, args, 0..1)
      if (args.isEmpty()) {
        XPathValue.Number(context.contextPosition.toDouble())
      } else {
        val arg = args[0]
        if (arg is XPathValue.NodeSet && arg.nodes.isNotEmpty()) {
          val target = arg.nodes.first()
          val pos =
            if (
              target is XPathNode.InstanceRootElementNode &&
                context.contextNode.parent === target &&
                (context.contextNode.siblingRepeatCount > 1 || context.contextPosition > 1)
            ) {
              context.contextPosition
            } else {
              target.repeatIndex
            }
          XPathValue.Number(pos.toDouble())
        } else {
          XPathValue.Number(context.contextPosition.toDouble())
        }
      }
    }
    define("last") {
      checkEagerArgCount(name, args, 0..0)
      XPathValue.Number(context.contextSize.toDouble())
    }
    define("instance") {
      checkEagerArgCount(name, args, 1..1)
      val instanceId = args[0].toXPathString()
      val root =
        context.secondaryInstanceProvider.resolveRoot(instanceId)
          ?: throw XPathEvaluationException("Secondary instance '$instanceId' not found")
      XPathValue.NodeSet(listOf(root))
    }
    define("current") {
      checkEagerArgCount(name, args, 0..0)
      XPathValue.NodeSet(listOf(context.currentQuestionNode))
    }
    define("randomize") {
      checkEagerArgCount(name, args, 1..2)
      val arg = args[0]
      if (arg !is XPathValue.NodeSet) {
        throw XPathEvaluationException(
          "Function 'randomize' requires a node-set as its first argument"
        )
      }
      val seedStr = args.getOrNull(1)?.toXPathString()
      val shuffled = CryptoAndPrngUtils.randomizeList(arg.nodes, seedStr, context.randomSeed)
      XPathValue.NodeSet(shuffled)
    }
  }
}
