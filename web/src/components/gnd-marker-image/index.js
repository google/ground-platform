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
import React from "react";

const png = filebase => require(`../../images/${filebase}.png`);

const iconSrc = iconId => {
  switch (iconId) {
    case "tree":
      return png("tree");
    case "house-map-marker":
      return png("home-map-marker");
    case "star-circle":
      return png("star-circle");
    default:
      return png("map-marker");
  }
};

const GndMarkerImage = ({ featureType, className }) => (
  <img
    width="24"
    height="24"
    src={featureType && iconSrc(featureType.iconId)}
    className={className}
    alt="Map marker"
  />
);

export default GndMarkerImage;
