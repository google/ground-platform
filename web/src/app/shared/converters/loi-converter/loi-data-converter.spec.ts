/**
 * Copyright 2022 Google LLC
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
import { DocumentData } from '@angular/fire/firestore';
import firebase from 'firebase';
import { GenericLocationOfInterest, LocationOfInterest } from '../../models/loi.model';
import { toGeometry } from '../geometry-converter';
import { LoiDataConverter } from './loi-data-converter';
import GeoPoint = firebase.firestore.GeoPoint;
import { Map } from 'immutable';
import { Geometry } from '../../models/geometry/geometry';

const x = -42.121;
const y = 28.482;
const geometryData0 = {
    type: 'Point',
    coordinates: new GeoPoint(x, y),
};

describe('LoiDataConverter_toLocationOfInterest', () => {
    let geometry0: Geometry;

    const geometry0WithError = toGeometry(geometryData0);
    if (geometryData0 instanceof Error) {
        throw new Error(`got unexpected error in geometry conversion ${geometry0WithError}`);
    }
    geometry0 = geometry0WithError as Geometry;

    const testData: {
        expectation: string;
        inputId: string;
        inputData: DocumentData;
        want: LocationOfInterest;
    }[] = [
            {
                expectation: "converts geometry with no properties",
                inputId: "id0",
                inputData: {
                    jobId: "jobId0",
                    geometry: geometryData0,
                    properties: {},
                },
                want: new GenericLocationOfInterest(
                    "id0",
                    "jobId0",
                    geometry0,
                    Map<string, string | number>()),
            },
            {
                expectation: "converts geometry with properties",
                inputId: "id0",
                inputData: {
                    jobId: "jobId0",
                    geometry: geometryData0,
                    properties: {
                        prop0: "value0",
                        prop1: 1,
                        prop2: "",
                    },
                },
                want: new GenericLocationOfInterest(
                    "id0",
                    "jobId0",
                    geometry0,
                    Map<string, string | number>([
                        ["prop0", "value0"],
                        ["prop1", 1],
                        ["prop2", ""]
                    ]),
                ),
            },
        ];

    for (let t of testData) {
        const got = LoiDataConverter.toLocationOfInterest(t.inputId, t.inputData);
        if (got instanceof Error) {
            throw new Error(`got unexpected error ${got}`);
        }

        it(t.expectation, () => expect(got as LocationOfInterest).toEqual(t.want));
    }
});

describe('LoiDataConverter_toLocationOfInterest_Error', () => {
    let geometry0: Geometry;

    const geometry0WithError = toGeometry(geometryData0);
    if (geometryData0 instanceof Error) {
        throw new Error(`got unexpected error in geometry conversion ${geometry0WithError}`);
    }
    geometry0 = geometry0WithError as Geometry;
    const testData: {
        expectation: string;
        inputId: string;
        inputData: DocumentData;
        wantErrorMessage: string;
    }[] = [
            {
                expectation: "Simple geometry with no properties",
                inputId: "id0",
                inputData: {
                    jobId: "",
                    geometry: geometryData0,
                    properties: {},
                },
                wantErrorMessage: 'missing job id',
            },
        ];

    for (let t of testData) {
        const got = LoiDataConverter.toLocationOfInterest(t.inputId, t.inputData);
        if (!(got instanceof Error)) {
            throw new Error(`expected error but instead got ${got}`);
        }

        it(t.expectation, () => expect((got as Error).message).toContain(t.wantErrorMessage));
    }
});

