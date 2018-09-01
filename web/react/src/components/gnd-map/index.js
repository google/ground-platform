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
import GoogleMap from "google-map-react";
import googleMapsConfig from "../../.google-maps-config.js";

class GndMap extends React.Component {
	// https://github.com/google-map-react/google-map-react/blob/master/API.md
	static defaultProps = {
		center: {
			lat: 0,
			lng: 0
		},
		zoom: 1
	};

	componentDidMount() {
		// this.refs.map;
	}

	createMapOptions(maps) {
    return {      
      disableDefaultUI: true,
  		zoomControl: true,
      mapTypeId: 'hybrid'
    }
  }

	onMapLoaded(map, maps) {
		console.log('here');
	}

	render() {
		return (
			<GoogleMap
				id="map"
				style={{
					width: "100vw",
					height: "100vh",
					position: "absolute",
					zIndex: -1
				}}
				bootstrapURLKeys={{ key: googleMapsConfig.apiKey }}
				options={(maps) => this.createMapOptions(maps)}
				onGoogleApiLoaded={({map, maps}) => this.onMapLoaded(map, maps)}
				defaultCenter={this.props.center}
				defaultZoom={this.props.zoom}
				yesIWantToUseGoogleMapApiInternals={true}
			/>
		);
	}
}
export default GndMap;
