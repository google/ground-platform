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
import angularTemplate from '@angular-eslint/template-parser';
import angularTemplatePlugin from '@angular-eslint/eslint-plugin-template';
import typescriptEslintPlugin from '@typescript-eslint/eslint-plugin';
import prettierPlugin from 'eslint-plugin-prettier';
import typescriptParser from '@typescript-eslint/parser';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
    {
        ignores: [
            "**/*.json",
            "**/node_modules",
            "**/build",
            "**/dist",
            "**/coverage",
            "src/test.ts",
            "**/src/generated"
        ],
    },
    ...gts,
    {
        // Global settings
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
                browser: "readonly",
                document: "readonly",
                window: "readonly",
                setTimeout: "readonly",
                setInterval: "readonly",
                clearTimeout: "readonly",
                clearInterval: "readonly",
                console: "readonly"
            }
        },
        rules: {
            "eqeqeq": ["warn", "always", { "null": "ignore" }],
            "object-curly-spacing": ["warn", "always"],
            "sort-imports": [
                "warn",
                {
                    "ignoreCase": false,
                    "ignoreDeclarationSort": true,
                    "ignoreMemberSort": false,
                    "memberSyntaxSortOrder": ["none", "all", "multiple", "single"],
                    "allowSeparatedGroups": true
                }
            ],
            "n/no-extraneous-import": "off",
            "n/no-missing-import": "off",
            "n/no-exports-assign": "off",
            "n/no-deprecated-api": "off",
            "n/no-unsupported-features/es-builtins": "off",
            "n/no-unsupported-features/es-syntax": "off",
            "n/no-unsupported-features/node-builtins": "off",
            "prettier/prettier": "warn"
        }
    },
    {
        files: ["**/*.ts"],
        languageOptions: {
            parser: typescriptParser,
            parserOptions: {
                project: ["tsconfig.json"],
                tsconfigRootDir: __dirname,
                sourceType: "module"
            }
        },
        plugins: {
            "@typescript-eslint": typescriptEslintPlugin,
            "prettier": prettierPlugin
        },
        rules: {
            "eqeqeq": ["warn", "always", { "null": "ignore" }],
            "prettier/prettier": "warn",
            "@typescript-eslint/no-unsafe-function-type": "off",
            "@typescript-eslint/no-empty-object-type": "off",
            "@typescript-eslint/no-wrapper-object-types": "off",
            "@typescript-eslint/no-explicit-any": "warn",
            "@typescript-eslint/no-unused-vars": ["warn", { "argsIgnorePattern": "^_" }],
            "@typescript-eslint/no-unused-expressions": "warn",
            "@typescript-eslint/ban-ts-comment": "off",
            "@typescript-eslint/triple-slash-reference": "off",
            "no-var": "off",
            "prefer-const": "off",
            "no-empty-function": "off",
            "no-shadow": "off",
            "no-duplicate-imports": "off",
            "no-redeclare": "off",
            // Requested disabled rules
            "@typescript-eslint/no-floating-promises": "off"
        }
    },
    {
        files: ["**/*.html"],
        languageOptions: {
            parser: angularTemplate,
        },
        plugins: {
            "@angular-eslint/template": angularTemplatePlugin,
            "prettier": prettierPlugin
        },
        rules: {
            "prettier/prettier": ["warn", { "parser": "angular" }],
            "@angular-eslint/template/prefer-control-flow": "off",
            "@angular-eslint/template/eqeqeq": "warn",
            "@angular-eslint/template/no-negated-async": "warn"
        }
    }
];
