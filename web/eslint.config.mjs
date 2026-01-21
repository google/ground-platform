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

import baseConfig from '../eslint.base.config.mjs';
import angularTemplate from '@angular-eslint/template-parser';
import angularTemplatePlugin from '@angular-eslint/eslint-plugin-template';
import typescriptEslintPlugin from '@typescript-eslint/eslint-plugin';
import typescriptParser from '@typescript-eslint/parser';
import importPlugin from 'eslint-plugin-import';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
  ...baseConfig,
  {
    ignores: ['src/test.ts'],
  },
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: ['tsconfig.json'],
        tsconfigRootDir: __dirname,
        sourceType: 'module',
      },
    },
    plugins: {
      '@typescript-eslint': typescriptEslintPlugin,
      import: importPlugin,
    },
    rules: {
      'import/no-duplicates': 'error',
      'no-var': 'error',
      'prefer-const': 'error',
      'no-empty-function': 'off',
      '@typescript-eslint/no-empty-function': 'off',
      'no-shadow': 'off',
      '@typescript-eslint/no-shadow': 'off',
      'no-redeclare': 'error',
      '@typescript-eslint/no-redeclare': 'error',
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/no-unused-expressions': 'warn',
      '@typescript-eslint/no-unsafe-function-type': 'off',
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/no-wrapper-object-types': 'off',
      '@typescript-eslint/ban-ts-comment': 'off',
      '@typescript-eslint/triple-slash-reference': 'off',
      '@typescript-eslint/no-floating-promises': 'off',
    },
  },
  {
    files: ['**/*.html'],
    languageOptions: { parser: angularTemplate },
    plugins: {
      '@angular-eslint/template': angularTemplatePlugin,
    },
    rules: {
      'prettier/prettier': ['warn', { parser: 'angular' }],
      '@angular-eslint/template/prefer-control-flow': 'off',
      '@angular-eslint/template/eqeqeq': 'warn',
      '@angular-eslint/template/no-negated-async': 'warn',
    },
  },
];
