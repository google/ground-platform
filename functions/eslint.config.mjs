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

import gts from 'gts/build/eslint.config.js';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
    {
        ignores: [
            "**/node_modules",
            "**/lib",
            "**/coverage"
        ],
    },
    ...gts,
    {
        // Global settings for all files
        languageOptions: {
            globals: {
                jasmine: "readonly",
                describe: "readonly",
                it: "readonly",
                expect: "readonly",
                beforeEach: "readonly",
                afterEach: "readonly",
                beforeAll: "readonly",
                afterAll: "readonly",
                spyOn: "readonly",
                fail: "readonly",
                require: "readonly",
                module: "readonly",
                process: "readonly"
            }
        },
        rules: {
            "eqeqeq": ["error", "always", { "null": "ignore" }],
            "object-curly-spacing": ["error", "always"],
            "prettier/prettier": "warn"
        }
    },
    {
        // TS-specific overrides
        files: ["**/*.ts"],
        languageOptions: {
            parserOptions: {
                project: ["tsconfig.json"],
                tsconfigRootDir: __dirname
            }
        },
        rules: {
            // Relaxed rules to match legacy behavior without code changes
            "@typescript-eslint/no-explicit-any": "warn",
            "@typescript-eslint/no-require-imports": "warn",
            "@typescript-eslint/no-unused-vars": "warn",
            "@typescript-eslint/no-wrapper-object-types": "warn",
            "@typescript-eslint/no-floating-promises": "off"
        }
    }
];
