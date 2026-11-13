/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { createClusterBubbleElement, getBubbleLabel } from './cluster-bubble';

describe('getBubbleLabel', () => {
  it('shows LOIs with data over all LOIs', () => {
    expect(getBubbleLabel(3, 12)).toBe('3/12');
    expect(getBubbleLabel(0, 1)).toBe('0/1');
  });
});

describe('createClusterBubbleElement', () => {
  it('shows the label as text', () => {
    const element = createClusterBubbleElement(2, 7);

    expect(element.textContent).toBe('2/7');
  });

  it('describes the bubble for assistive technology', () => {
    expect(createClusterBubbleElement(2, 7).getAttribute('aria-label')).toBe(
      '7 locations, 2 with data'
    );
    expect(createClusterBubbleElement(0, 1).getAttribute('aria-label')).toBe(
      '1 location, 0 with data'
    );
  });

  it('is centered on the marker position', () => {
    expect(createClusterBubbleElement(0, 1).style.transform).toBe(
      'translateY(50%)'
    );
  });
});
