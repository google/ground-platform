/**
 * Copyright 2024 The Ground Authors.
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

import { registry } from "./message-registry";
import { DocumentData } from "@google-cloud/firestore";

export function toDocumentData(message: any): DocumentData | Error {
    const type = message.constructor.name;
    const descriptor = registry.nested[type];
    if (!descriptor?.fields) return Error(`Unknown message type $type`);

    const firestoreMap: DocumentData = {};
    for (const name in descriptor.fields) {
        const field = descriptor.fields[name];
        if (!field) {
            console.debug(`Skipping unknown field $name in $type`);
            continue;
        }
        const value = message[name];
        if (value && !isEmpty(value)) {
            const fieldNumber = descriptor.fields[name].id;
            firestoreMap[fieldNumber.toString()] = value;
        }
    }
    return firestoreMap;
}
  
function isEmpty(obj: any) {
    switch (typeof obj) {
        case "string":
            return obj === "";
        case "object":
            return Object.keys(obj).length === 0;
        default:
            return false;
    }
}