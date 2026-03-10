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

import { NgModule } from '@angular/core';
import { MatIconModule, MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

// TODO: Make custom svgs match the color of the job icon
const POINT_ICON = `
  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path fill-rule="evenodd" clip-rule="evenodd" d="M2 9.42442V10.5756C2 12.2568 2.20513 14.0009 2.61538 14.9235C3.02564 15.8462 3.84615 16.7697 4.87179 17.2825C5.89744 17.7953 7.74359 18 9.42442 18H10.5756C12.2564 18 14.1026 17.7953 15.1282 17.2825C16.1538 16.7697 16.9744 15.8462 17.3846 14.9235C17.7949 14.0009 18 12.2568 18 10.5756V9.42442C18 7.74316 17.7949 5.99914 17.3846 5.0765C16.9744 4.15385 16.1538 3.23034 15.1282 2.71752C14.1026 2.2047 12.2564 2 10.5756 2H9.42442C7.74359 2 5.89744 2.2047 4.87179 2.71752C3.84615 3.23034 3.02564 4.15385 2.61538 5.0765C2.20513 5.99914 2 7.74316 2 9.42442ZM8.03828 7.23828C7.59645 7.23828 7.23828 7.59645 7.23828 8.03828V11.9621C7.23828 12.4039 7.59645 12.7621 8.03828 12.7621H11.9621C12.4039 12.7621 12.7621 12.4039 12.7621 11.9621V8.03828C12.7621 7.59645 12.4039 7.23828 11.9621 7.23828H8.03828Z" fill="currentColor"/>
  </svg>
`;

const POLYGON_ICON = `
  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
    <ellipse cx="3.99411" cy="16.2549" rx="2.07712" ry="2.07712" fill="currentColor"/>
    <ellipse cx="16.2558" cy="16.2549" rx="2.07712" ry="2.07712" fill="currentColor"/>
    <ellipse cx="16.1884" cy="10.0244" rx="2.07712" ry="2.07712" fill="currentColor"/>
    <circle cx="3.99411" cy="3.74314" r="2.07712" fill="currentColor"/>
    <rect x="6.6084" y="15.2676" width="7.03542" height="1.87611" fill="currentColor"/>
    <rect x="7.04492" y="4.01172" width="8.20297" height="2.07712" transform="rotate(25.8625 7.04492 4.01172)" fill="currentColor"/>
    <rect x="15.2168" y="13.6602" width="1.03856" height="2.07712" transform="rotate(-90 15.2168 13.6602)" fill="currentColor"/>
    <rect x="2.85449" y="13.3906" width="7.03542" height="2.34514" transform="rotate(-90 2.85449 13.3906)" fill="currentColor"/>
  </svg>
`;

@NgModule({
  exports: [MatIconModule],
})
export class GroundIconModule {
  constructor(iconRegistry: MatIconRegistry, sanitizer: DomSanitizer) {
    // Add custom loi icons for job list items
    iconRegistry.addSvgIconLiteral(
      'point',
      sanitizer.bypassSecurityTrustHtml(POINT_ICON)
    );
    iconRegistry.addSvgIconLiteral(
      'polygon',
      sanitizer.bypassSecurityTrustHtml(POLYGON_ICON)
    );
  }
}
