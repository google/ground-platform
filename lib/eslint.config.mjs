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
