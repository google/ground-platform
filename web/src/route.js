/**
 * @license
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { matchPath } from "react-router";
import { getLocation } from "connected-react-router";

export const featureDisplayPath = "/p/:projectId/f/:featureId"
export const projectPath = "/p/:projectId"

// Match selector will choose first match so put them in order.
export const paths = [
  featureDisplayPath,
  projectPath,
];

// It only makes sense to recalculate the `matchPath` whenever the pathname
// of the location changes. That's why `createMatchSelector` memoizes
// the latest result based on the location's pathname.
// Note: function based on createMatchSelector from "connected-react-router".
export const createMatchSelector = () => {
  let lastPathname = null
  let lastMatch = null

  return state => {
    const { pathname } = getLocation(state) || {}
    if (pathname === lastPathname) {
      return lastMatch
    }
    lastPathname = pathname
    for (var i in paths) {
      const match = matchPath(pathname, paths[i])
      if (!match || !lastMatch || match.url !== lastMatch.url) {
        lastMatch = match
        return lastMatch
      }
    }
    return lastMatch
  }
}
