/**
 * Copyright 2019 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import 'zone.js/testing';
import { getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting,
} from '@angular/platform-browser-dynamic/testing';

// Mock Google Maps API
class MockMVCArray {
  constructor(private array: any[]) {}
  getLength() { return this.array.length; }
  getAt(i: number) { return this.array[i]; }
  forEach(cb: (elem: any, i: number) => void) { this.array.forEach(cb); }
}

// Define classes outside to allow inter-class usage
class MockLatLng {
  constructor(private _lat: number, private _lng: number) {}
  lat() { return this._lat; }
  lng() { return this._lng; }
  toJSON() { return {lat: this._lat, lng: this._lng}; }
}

class MockLatLngBounds {
  private sw: MockLatLng | null = null;
  private ne: MockLatLng | null = null;
  constructor(sw?: MockLatLng, ne?: MockLatLng) { 
      this.sw = sw || null; 
      this.ne = ne || sw || null; 
  }
  extend(point: MockLatLng) {
    if (!this.sw || !this.ne) { 
        this.sw = new MockLatLng(point.lat(), point.lng()); 
        this.ne = new MockLatLng(point.lat(), point.lng());
        return;
    }
    const newMinLat = Math.min(this.sw.lat(), point.lat());
    const newMaxLat = Math.max(this.ne.lat(), point.lat());
    const newMinLng = Math.min(this.sw.lng(), point.lng());
    const newMaxLng = Math.max(this.ne.lng(), point.lng()); // Simplified logic, doesn't handle anti-meridian
    
    this.sw = new MockLatLng(newMinLat, newMinLng);
    this.ne = new MockLatLng(newMaxLat, newMaxLng);
  }
  getNorthEast() { return this.ne; }
  getSouthWest() { return this.sw; }
}

const googleMock = {
  maps: {
    LatLng: MockLatLng,
    LatLngBounds: MockLatLngBounds,
    MapTypeId: {
      HYBRID: 'hybrid',
      ROADMAP: 'roadmap',
      SATELLITE: 'satellite',
      TERRAIN: 'terrain'
    },
    OverlayView: class {},
    Marker: class {},
    marker: {
      AdvancedMarkerElement: class {
        listeners: any = {};
        position: any;
        map: any;
        element: HTMLElement;
        gmpDraggable: boolean = false;
        gmpClickable: boolean = true;
        
        constructor(public options?: any) {
             if(options) {
                 this.position = options.position;
                 this.map = options.map;
                 this.gmpClickable = options.gmpClickable;
             }
             this.element = document.createElement('div');
             if(options?.content) this.element.appendChild(options.content);
        }
        addListener(event: string, handler: Function) {
            if(!this.listeners[event]) this.listeners[event] = [];
            this.listeners[event].push(handler);
            return { remove: () => {} };
        }
      }
    },
    Map: class {
        googleMap: any = this;
        listeners: any = {};
        
        setOptions() {}
        setCenter() {}
        setZoom() {}
        fitBounds() {}
        panTo() {}
        getZoom() { return 10; }
        addListener(event: string, handler: Function) {
            if(!this.listeners[event]) this.listeners[event] = [];
            this.listeners[event].push(handler);
             return { remove: () => {} };
        }
    },
    Polygon: class {
       listeners: any = {};
       private paths: MockMVCArray;
       private props: any = {};
       
       constructor(public opts: any) {
           this.paths = new MockMVCArray((opts.paths || []).map((p: any) => new MockMVCArray(p)));
           this.props = {...opts};
       }
       set(key: string, val: any) { this.props[key] = val; }
       get(key: string) { return this.props[key]; }
       setMap(map: any) { this.props.map = map; }
       getMap() { return this.props.map; }
       getPaths() { return this.paths; }
       setOptions(opts: any) { Object.assign(this.props, opts); }
       addListener(event: string, handler: Function) {
            if(!this.listeners[event]) this.listeners[event] = [];
            this.listeners[event].push(handler);
            return { remove: () => {} };
       }
    },
    MVCArray: MockMVCArray,
    event: {
        trigger: (instance: any, eventName: string, ...args: any[]) => {
            if (instance && instance.listeners && instance.listeners[eventName]) {
                instance.listeners[eventName].forEach((fn: any) => fn(...args));
            }
        },
        addListener: (instance: any, eventName: string, handler: Function) => {
             if (instance.addListener) {
                 return instance.addListener(eventName, handler);
             }
             // Fallback if instance doesn't have addListener (shouldn't happen with our mocks)
             return { remove: () => {} };
        }
    },
    geometry: {
        poly: {
            containsLocation: () => true
        },
        spherical: {
            computeArea: () => 1
        }
    },
    Data: {
        MouseEvent: class {}
    }
  }
};
(window as any).google = googleMock;

// First, initialize the Angular testing environment.
getTestBed().initTestEnvironment(
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting()
);
