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

import kotlin.math.floor
import org.openforis.collectone.v2.core.forms.xpath.model.TemporalUtils
import org.openforis.collectone.v2.core.forms.xpath.model.XPathValue

/** ODK date and time functions. */
internal object DateTimeFunctions {

  val table: Map<String, EagerXPathFunction> = xpathFunctionTable {
    define("today") {
      checkEagerArgCount(name, args, 0..0)
      val epochMillis = context.clockEpochMillis()
      val localMillis = epochMillis + context.utcOffsetMillis(epochMillis)
      val epochDays = floor(localMillis / 86400000.0).toLong()
      val date = TemporalUtils.epochDaysToDate(epochDays)
      XPathValue.DateVal(date)
    }
    define("now") {
      checkEagerArgCount(name, args, 0..0)
      val epochDays = context.clockEpochMillis() / 86400000.0
      val instant = TemporalUtils.epochDaysToInstant(epochDays)
      XPathValue.TimestampVal(instant)
    }
    define("date") {
      checkEagerArgCount(name, args, 1..1)
      val arg = args[0].unwrapNodeSet()
      when (arg) {
        is XPathValue.DateVal -> arg
        else -> {
          val str = arg.toXPathString()
          TemporalUtils.tryParseDate(str)?.let {
            return@define XPathValue.DateVal(it)
          }
          val num = arg.toNumber()
          if (!num.isNaN()) {
            XPathValue.DateVal(TemporalUtils.epochDaysToDate(floor(num).toLong()))
          } else {
            XPathValue.Str("")
          }
        }
      }
    }
    define("date-time") {
      checkEagerArgCount(name, args, 1..1)
      val arg = args[0].unwrapNodeSet()
      when (arg) {
        is XPathValue.TimestampVal -> arg
        is XPathValue.DateVal ->
          XPathValue.TimestampVal(TemporalUtils.epochDaysToInstant(arg.toNumber()))
        else -> {
          val epochDays = TemporalUtils.tryParseToEpochDays(arg.toXPathString()) ?: arg.toNumber()
          if (!epochDays.isNaN()) {
            XPathValue.TimestampVal(TemporalUtils.epochDaysToInstant(epochDays))
          } else {
            XPathValue.Str("")
          }
        }
      }
    }
    define("decimal-date-time") {
      checkEagerArgCount(name, args, 1..1)
      val arg = args[0].unwrapNodeSet()
      XPathValue.Number(arg.toNumber())
    }
    define("decimal-time") {
      checkEagerArgCount(name, args, 1..1)
      val arg = args[0].unwrapNodeSet()
      when (arg) {
        is XPathValue.TimeVal -> XPathValue.Number(TemporalUtils.timeOfDayToDecimal(arg.value))
        else -> {
          val time = TemporalUtils.tryParseTime(arg.toXPathString())
          if (time != null) XPathValue.Number(TemporalUtils.timeOfDayToDecimal(time))
          else XPathValue.Number(Double.NaN)
        }
      }
    }
    define("format-date", "format-date-time") {
      checkEagerArgCount(name, args, 2..2)
      val epochDays = args[0].toNumber()
      val pattern = args[1].toXPathString()
      XPathValue.Str(TemporalUtils.formatPattern(epochDays, pattern))
    }
  }
}
