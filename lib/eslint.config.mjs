/**
 * Copyright 2025 The Ground Authors.
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

import { FlatCompat } from '@eslint/eslintrc';
import js from '@eslint/js';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
});

export default [
    {
        ignores: [
            "**/*.json",
            "**/node_modules",
            "**/build",
            "**/dist",
            "**/coverage",
            "src/test.ts"
        ],
    },
    ...compat.config({
        extends: "../node_modules/gts",
        env: {
            jasmine: true
        },
        parser: "@typescript-eslint/parser",
        plugins: ["@typescript-eslint"],
        root: true,
        rules: {
            "eqeqeq": ["error", "always", { "null": "ignore" }],
            "object-curly-spacing": ["error", "always"],
            "n/no-extraneous-require": ["error", {
                "allowModules": ["firebase-admin"]
            }],
            // Relaxed rules to match legacy behavior without code changes
            "@typescript-eslint/no-explicit-any": "warn",
            "@typescript-eslint/no-require-imports": "warn",
            "prettier/prettier": "warn",
            "@typescript-eslint/no-wrapper-object-types": "warn"
        }
    })
];
