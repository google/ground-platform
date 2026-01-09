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

/**
 * An asynchronous iterator class that allows for iterating over the results of a Firestore query in batches.
 */
export class QueryIterator implements AsyncIterator<QueryDocumentSnapshot> {
  private querySnapshot: QuerySnapshot | null = null;
  private currentIndex = 0;
  private lastDocument: QueryDocumentSnapshot | null = null;

  /**
   * Creates a new QueryIterator.
   *
   * @param query The Firestore query to iterate over.
   * @param pageSize The number of documents to fetch in each batch.
   */
  constructor(
    private query: Query,
    private pageSize: number
  ) {}

  /**
   * Fetches the next batch of documents and returns the next document in the iterator.
   *
   * @returns A promise that resolves to an `IteratorResult` object. The `value` property
   *          will be the next `QueryDocumentSnapshot` if there are more documents, or `undefined`
   *          if there are no more documents. The `done` property indicates whether there are
   *          more documents to iterate over.
   */
  async next(): Promise<IteratorResult<QueryDocumentSnapshot>> {
    if (
      this.querySnapshot === null ||
      this.currentIndex >= this.querySnapshot.size
    ) {
      // Fetch next batch of documents
      let q = this.query.limit(this.pageSize);
      if (this.lastDocument) {
        q = q.startAfter(this.lastDocument);
      }
      this.querySnapshot = await q.get();
      this.currentIndex = 0;
    }
    if (this.querySnapshot.size > 0) {
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

/**
 * Performs a left outer join operation on two asynchronous iterators with sorting.
 *
 * This function iterates through an asynchronous iterator of left elements (`leftIterator`)
 * and an asynchronous iterator of right elements (`rightIterator`). It performs a left outer join
 * based on the keys extracted from each element using the provided functions `getLeftKey` and `getRightKey`.
 *
 * The function yields pairs of elements from the left and right iterators. If there's no matching element
 * from the right iterator for a left element, the function yields a pair with the left element's value and
 * `undefined` for the right element (left outer join behavior).
 *
 * Both iterators are expected to be sorted by their respective keys for optimal performance.
 *
 * @template T The type of elements in the left iterator.
 * @template U The type of elements in the right iterator.
 *
 * @param leftIterator The asynchronous iterator of left elements.
 * @param getLeftKey A function that extracts the key for comparison from a left element.
 * @param rightIterator The asynchronous iterator of right elements.
 * @param getRightKey A function that extracts the key for comparison from a right element.
 *
 * @returns An asynchronous generator that yields pairs of elements from the left and right iterators.
 *          Each pair is an array containing the left element's value and the matching right element's value
 *          (or `undefined` if no match is found).
 */
export async function* leftOuterJoinSorted<T, U>(
  leftIterator: AsyncIterator<T>,
  getLeftKey: (left: T) => any,
  rightIterator: AsyncIterator<U>,
  getRightKey: (right: U) => any
): AsyncGenerator<[T, U | undefined]> {
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
      // If no matching items were found on the right side for the current left item
      // (or the right iterator has reached its end), yield a pair
      // consisting of the left item's value and undefined.
      if (rightItemsFound === 0) yield [leftItem.value, undefined];
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
      yield [leftItem.value, rightItem.value];
      rightItem = await rightIterator.next();
    }
  }
}
