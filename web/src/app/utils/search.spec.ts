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

import { foldForSearch, matchesSearch } from './search';

describe('foldForSearch', () => {
  it('lower-cases and removes Latin accents', () => {
    expect(foldForSearch('Égypte Ñandú')).toBe('egypte nandu');
  });

  it('folds Vietnamese Đ to d', () => {
    expect(foldForSearch('Đức')).toBe('duc');
  });

  it('keeps the marks of non-Latin scripts', () => {
    expect(foldForSearch('ແບບສຳຫຼວດ')).toBe('ແບບສຳຫຼວດ'.normalize('NFD'));
  });
});

describe('matchesSearch', () => {
  it('matches everything when the query is blank', () => {
    expect(matchesSearch('   ', 'anything')).toBeTrue();
  });

  it('ignores case and accents', () => {
    expect(matchesSearch('foret', 'Inventaire de la Forêt')).toBeTrue();
  });

  it('requires every word, in any order and any field', () => {
    expect(matchesSearch('kenya forest', 'Forest plots', 'Kenya')).toBeTrue();
    expect(matchesSearch('kenya forest', 'Forest plots', 'Ghana')).toBeFalse();
  });

  it('treats a missing field as empty', () => {
    expect(matchesSearch('x', undefined)).toBeFalse();
  });
});
