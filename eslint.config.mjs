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

import gts from 'gts/build/eslint.config.js';
import angularTemplate from '@angular-eslint/template-parser';
import angularTemplatePlugin from '@angular-eslint/eslint-plugin-template';
import typescriptEslintPlugin from '@typescript-eslint/eslint-plugin';
import typescriptParser from '@typescript-eslint/parser';
import importPlugin from 'eslint-plugin-import';
import prettierPlugin from 'eslint-plugin-prettier';
import unusedImports from 'eslint-plugin-unused-imports';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default [
  {
    ignores: [
      '**/*.json',
      '**/node_modules',
      '**/build',
      '**/dist',
      '**/coverage',
      '**/src/generated',
      'web/src/test.ts',
    ],
  },
  ...gts.map(config => ({
    ...config,
    ...(config.languageOptions?.parserOptions
      ? {
          languageOptions: {
            ...config.languageOptions,
            parserOptions: {},
          },
        }
      : {}),
  })),
  {
    plugins: {
      '@typescript-eslint': typescriptEslintPlugin,
      prettier: prettierPlugin,
      'unused-imports': unusedImports,
    },
    languageOptions: {
      globals: {
        jasmine: 'readonly',
        describe: 'readonly',
        it: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        afterAll: 'readonly',
        spyOn: 'readonly',
        fail: 'readonly',
        browser: 'readonly',
        document: 'readonly',
        window: 'readonly',
        setTimeout: 'readonly',
        setInterval: 'readonly',
        clearTimeout: 'readonly',
        clearInterval: 'readonly',
        console: 'readonly',
        // 'unused-imports': 'readonly' // Is this needed? No, it's a plugin.
      },
    },
    rules: {
      eqeqeq: ['warn', 'always', { null: 'ignore' }],
      'object-curly-spacing': ['warn', 'always'],
      'sort-imports': [
        'warn',
        {
          ignoreCase: false,
          ignoreDeclarationSort: true,
          ignoreMemberSort: false,
          memberSyntaxSortOrder: ['none', 'all', 'multiple', 'single'],
          allowSeparatedGroups: true,
        },
      ],
      'n/no-extraneous-import': 'off',
      'n/no-missing-import': 'off',
      'prettier/prettier': 'warn',
      'no-unused-vars': 'off',
      'unused-imports/no-unused-imports': 'error',
      'unused-imports/no-unused-vars': [
        'warn',
        {
          vars: 'all',
          varsIgnorePattern: '^_',
          args: 'after-used',
          argsIgnorePattern: '^_',
        },
      ],
    },
  },
  {
    files: ['e2e-tests/**/*.ts'],
    languageOptions: {
      globals: {
        jasmine: 'readonly',
      },
      parserOptions: {
        project: ['./e2e-tests/tsconfig.json'],
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      'n/no-unpublished-import': [
        'error',
        {
          allowModules: ['jasmine'],
        },
      ],
    },
  },
  {
    files: ['functions/**/*.ts'],
    languageOptions: {
      globals: {
        require: 'readonly',
        module: 'readonly',
        process: 'readonly',
      },
      parserOptions: {
        project: ['./functions/tsconfig.json'],
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-require-imports': 'warn',
      '@typescript-eslint/no-wrapper-object-types': 'warn',
      '@typescript-eslint/no-floating-promises': 'off',
    },
  },
  {
    files: ['lib/**/*.ts', 'lib/**/*.js'],
    languageOptions: {
      globals: {
        require: 'readonly',
        module: 'readonly',
        process: 'readonly',
      },
      parserOptions: {
        project: ['./lib/tsconfig.json'],
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      'n/no-extraneous-require': [
        'error',
        {
          allowModules: ['firebase-admin'],
        },
      ],
      '@typescript-eslint/no-require-imports': 'warn',
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-wrapper-object-types': 'warn',
      '@typescript-eslint/no-floating-promises': 'off',
    },
  },
  {
    files: ['web/src/**/*.ts'],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: ['./web/tsconfig.json'],
        tsconfigRootDir: __dirname,
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
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
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
    files: ['web/src/**/*.html'],
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
