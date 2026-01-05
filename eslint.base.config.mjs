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
import prettierPlugin from 'eslint-plugin-prettier';

export default [
  {
    ignores: [
      '**/*.json',
      '**/node_modules',
      '**/build',
      '**/dist',
      '**/coverage',
      '**/src/generated',
    ],
  },
  ...gts,
  {
    plugins: {
      prettier: prettierPlugin,
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
    },
  },
];
