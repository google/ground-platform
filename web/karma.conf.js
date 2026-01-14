/**
 * Copyright 2019 The Ground Authors.
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

// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

const path = require('path');
const puppeteer = require('puppeteer');

process.env.CHROME_BIN = puppeteer.executablePath();

const isAgent = !!process.env.ANTIGRAVITY_AGENT || !!process.env.GEMINI_CLI;
const isCi = !!process.env.CI;
const isHeadless = isCi || isAgent;

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      'karma-jasmine',
      'karma-chrome-launcher',
      'karma-jasmine-html-reporter',
      'karma-coverage',
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      clearContext: false, // leave Jasmine Spec Runner output visible in browser
    },
    jasmineHtmlReporter: {
      suppressAll: true, // removes the duplicated traces
    },
    coverageReporter: {
      dir: path.join(__dirname, './coverage/web'),
      subdir: '.',
      reporters: [
        { type: 'html' },
        { type: 'lcovonly' },
        { type: 'text-summary' },
        { type: 'json-summary' },
      ],
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: !isHeadless,
    browsers: isHeadless ? ['ChromeHeadlessNoSandbox'] : ['Chrome'],
    singleRun: isHeadless,
    restartOnFileChange: !isHeadless,
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        // Required to run Chrome as root in test Docker image.
        flags: ['--no-sandbox'],
      },
    },
    files: ['google-maps-api.js'],
  });
};
