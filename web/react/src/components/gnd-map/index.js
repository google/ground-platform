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
import './index.css'
import { compose, withProps } from "recompose"
import { withScriptjs, withGoogleMap, GoogleMap, Marker } from "react-google-maps"
import googleMapsConfig from "../../.google-maps-config.js";

class GndMap extends React.Component {
	// https://github.com/google-map-react/google-map-react/blob/master/API.md
	constructor(props) {
		super(props);
		let gndMap = this;
		this.map = new Promise(r => {
			gndMap.resolveMap = r;
		});
	}

	onMapLoaded(map, maps) {
		this.resolveMap(map);
	}

	renderFeature(markers, key, feature) {
		if (!feature) {
			return markers;
		}

		const pos = feature.center;
		if (!pos) {
			return markers;
		}


		markers.push((<Marker 
			key={key}
			position={{lat: pos.latitude, lng: pos.longitude}} />));
		return markers;
	}

	render() {
		const {features} = this.props;
		console.log(features);
		return (
			<GoogleMap
				bootstrapURLKeys={{ key: googleMapsConfig.apiKey }}
				onGoogleApiLoaded={({map, maps}) => this.onMapLoaded(map, maps)}
				defaultCenter={this.props.center}
				defaultZoom={this.props.zoom}
	      defaultMapTypeId="hybrid"
	      defaultOptions={{
		      disableDefaultUI: true,
		  		zoomControl: true,
  		    scrollwheel: true,	
	      }}
			>
			{features && Object.keys(features).reduce((markers, key) => this.renderFeature(markers, key, features[key]), [])}
			</GoogleMap>
		);
	}
}

const mapsApiUrl = "https://maps.googleapis.com/maps/api/js?v=3.exp&libraries=geometry,drawing";

const enhance = compose(
  withProps({
    googleMapURL: `${mapsApiUrl}&key=${googleMapsConfig.apiKey}`,
    loadingElement: <div id="loading" />,
    containerElement: <div id="map-container" />,
    mapElement: <div id="map" />,
		center: {
			lat: 0,
			lng: 0
		},
		zoom: 3
  }),
  withScriptjs,
  withGoogleMap
);

export default enhance(GndMap);
