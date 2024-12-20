/**
 * Copyright 2024 The Ground Authors.
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

import {
  Query,
  QueryDocumentSnapshot,
  QuerySnapshot,
} from 'firebase-admin/firestore';

export class QueryIterator implements AsyncIterator<QueryDocumentSnapshot> {
  private querySnapshot: QuerySnapshot | null = null;
  private currentIndex = 0;
  private lastDocument: QueryDocumentSnapshot | null = null;

  constructor(
    private query: Query,
    private pageSize: number,
    private orderField: string
  ) {}

  async next(): Promise<IteratorResult<QueryDocumentSnapshot>> {
    if (
      this.querySnapshot === null ||
      this.currentIndex >= this.querySnapshot.docs.length
    ) {
      // Fetch next batch of documents
      let q = this.query.limit(this.pageSize);
      if (this.lastDocument) {
        q = q.startAfter([this.lastDocument?.get(this.orderField)]);
      }
      this.querySnapshot = await q.get();
      this.currentIndex = 0;
    }
    if (this.querySnapshot.docs.length > 0) {
      const document = this.querySnapshot.docs[this.currentIndex++];
      this.lastDocument = document; // Update last document for next batch
      return {
        value: document,
        done: false,
      };
    } else {
      return {
        value: undefined,
        done: true,
      };
    }
  }
}

export async function* leftOuterJoinSorted<T, U>(
  leftIterator: AsyncIterator<T>,
  getLeftKey: (left: T) => any,
  rightIterator: AsyncIterator<U>,
  getRightKey: (right: U) => any
): AsyncGenerator<[T, U | undefined, number]> {
  let leftItem = await leftIterator.next();
  let rightItem = await rightIterator.next();
  let rightItemsFound = 0;

  // This loop iterates through the left iterator until it's exhausted.
  // In each iteration, it compares the current left item's key with the current
  // right item's key (if there's a right item remaining).
  while (!leftItem.done) {
    const leftKey = getLeftKey(leftItem.value);
    const rightKey = rightItem.done ? undefined : getRightKey(rightItem.value);
    // Check for these conditions:
    // 1. Right iterator is done (no more items on the right).
    // 2. Left item's key is less than the right item's key (mismatch).
    if (rightItem.done || leftKey < rightKey) {
      // The left item has no matching item on the right (or right iterator is done).
      // Yield a pair with the left item's value and undefined for the right side.
      yield [leftItem.value, undefined, rightItemsFound];
      // Move to the next left item and reset the counter for matches.
      leftItem = await leftIterator.next();
      rightItemsFound = 0;
    } else if (leftKey > rightKey) {
      // The right item's key is less than the left item's key (mismatch).
      // Advance the right iterator to find a possible match for the current left item.
      rightItem = await rightIterator.next();
    } else {
      // Match found! The keys of the left and right items are equal.
      // Increment the counter for the number of matches found for the current left item.
      rightItemsFound++;
      // Yield a pair with the left item's value, the matching right item's value,
      // and the current count of matches for the left item.
      yield [leftItem.value, rightItem.value, rightItemsFound];
      rightItem = await rightIterator.next();
    }
  }
}
