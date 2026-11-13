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

/** Diameter of the bubble, and the space between its two circles. */
const BUBBLE_SIZE_PX = 68;
const BUBBLE_INSET_PX = 12;

/** Colors of the outer (translucent) and inner circles, as in Ground Android. */
const OUTER_COLOR = 'rgba(109, 221, 129, 0.29)';
const INNER_COLOR = '#6ddd81';

/** Returns the text of a bubble: LOIs with submissions over all of its LOIs. */
export function getBubbleLabel(withData: number, total: number): string {
  return `${withData}/${total}`;
}

/**
 * Creates the element shown by a map marker standing for a group of LOIs. The
 * look and the label follow the cluster bubble of Ground Android.
 *
 * Markers are anchored at the bottom center of their element, so the element
 * is shifted down by half its height to be centered on the position instead.
 */
export function createClusterBubbleElement(
  withData: number,
  total: number
): HTMLElement {
  const label = getBubbleLabel(withData, total);

  const bubble = document.createElement('div');
  bubble.className = 'cluster-bubble';
  bubble.setAttribute('role', 'button');
  bubble.setAttribute(
    'aria-label',
    `${total} ${total === 1 ? 'location' : 'locations'}, ${withData} with data`
  );
  Object.assign(bubble.style, {
    width: `${BUBBLE_SIZE_PX}px`,
    height: `${BUBBLE_SIZE_PX}px`,
    borderRadius: '50%',
    background: OUTER_COLOR,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transform: 'translateY(50%)',
    cursor: 'pointer',
  });

  const inner = document.createElement('div');
  Object.assign(inner.style, {
    width: `${BUBBLE_SIZE_PX - 2 * BUBBLE_INSET_PX}px`,
    height: `${BUBBLE_SIZE_PX - 2 * BUBBLE_INSET_PX}px`,
    borderRadius: '50%',
    background: INNER_COLOR,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#000',
    fontWeight: 'bold',
    fontSize: '12px',
    fontFamily: 'Roboto, sans-serif',
  });
  // Not `innerHTML`: the label is built from numbers, but keep it text anyway.
  inner.textContent = label;

  bubble.appendChild(inner);
  return bubble;
}
