/**
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { GroundPinService } from './ground-pin.service';

describe('GroundPinService', () => {
  let service: GroundPinService;

  beforeEach(() => {
    service = new GroundPinService();
  });

  it('uses valid hex colors as they are', () => {
    for (const color of ['#abc', '#abcd', '#ff9131', '#FF913180']) {
      expect(service.renderPin({ color })).toContain(`fill="${color}"`);
    }
  });

  it('falls back to the default color for values that are not hex colors', () => {
    const malicious = 'x"/><img src=x onerror="alert(1)">';
    for (const color of [malicious, 'red', '#12345', '#ggg', '', '#fff"']) {
      const svg = service.renderPin({ color, text: '1' });
      expect(svg).not.toContain('<img');
      expect(svg).not.toContain('onerror');
      expect(svg).toContain('fill="#ff0000"');
    }
  });

  it('does not let a malicious color reach the DOM', () => {
    const pin = service.createPinElement(
      'x"/><img src=x onerror="alert(1)">',
      '1'
    );
    expect(pin.querySelector('img')).toBeNull();
    expect(pin.innerHTML).not.toContain('onerror');
  });

  it('escapes the text', () => {
    const svg = service.renderPin({
      color: '#fff',
      text: '<script>x</script>',
    });
    expect(svg).not.toContain('<script>');
    expect(svg).toContain('&lt;script&gt;');
  });

  it('encodes a sanitized color in the image data URL', () => {
    const source = service.getPinImageSource('x"/><img onerror=1>');
    const svg = atob(source.substring(GroundPinService.urlPrefix.length));
    expect(svg).not.toContain('onerror');
    expect(svg).toContain('fill="#ff0000"');
  });
});
