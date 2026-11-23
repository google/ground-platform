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

import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** ODK identifier and encoding functions: `uuid`, `digest` and `base64-decode`. */
internal object CryptoFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("uuid") {
      checkEagerArgCount(name, args, 0..1)
      val len = args.firstOrNull()?.toNumber()?.toInt()
      XPathValue.Str(CryptoAndPrngUtils.generateUuidOrToken(len, context.randomSeed))
    }
    define("digest") {
      checkEagerArgCount(name, args, 2..3)
      val payload = args[0].toXPathString()
      val algo = args[1].toXPathString()
      val encoding = args.getOrNull(2)?.toXPathString() ?: "hex"
      XPathValue.Str(CryptoAndPrngUtils.digest(payload, algo, encoding))
    }
    define("base64-decode") {
      checkEagerArgCount(name, args, 1..1)
      XPathValue.Str(CryptoAndPrngUtils.base64Decode(args[0].toXPathString()))
    }
  }
}
