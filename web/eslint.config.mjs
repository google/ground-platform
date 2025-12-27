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
            "src/test.ts",
            "**/src/generated"
        ],
    },
    ...compat.config({
        extends: [
            "../node_modules/gts"
        ],
        env: {
            jasmine: true,
            browser: true,
            es2022: true
        },
        parser: "@typescript-eslint/parser",
        plugins: ["@typescript-eslint"],
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
            "n/no-missing-import": "off",
            "n/no-exports-assign": "off",
            "n/no-deprecated-api": "off",
            "n/no-unsupported-features/es-builtins": "off",
            "n/no-unsupported-features/es-syntax": "off",
            "n/no-unsupported-features/node-builtins": "off"
        },
        overrides: [
            {
                files: ["*.ts", "*.js"],
                extends: [
                    "plugin:@typescript-eslint/recommended",
                    "plugin:prettier/recommended"
                ],
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
                    "no-redeclare": "off"
                }
            },
            {
                files: ["*.html"],
                parser: "@angular-eslint/template-parser",
                extends: [
                    "plugin:@angular-eslint/template/recommended",
                    "plugin:prettier/recommended"
                ],
                rules: {
                    "prettier/prettier": ["warn", { "parser": "angular" }],
                    "@angular-eslint/template/prefer-control-flow": "off",
                    "@angular-eslint/template/eqeqeq": "warn",
                    "@angular-eslint/template/no-negated-async": "warn"
                }
            }
        ]
    })
];
