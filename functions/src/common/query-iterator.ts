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

  constructor(private query: Query, private pageSize: number) {}

  async next(): Promise<IteratorResult<QueryDocumentSnapshot>> {
    if (
      this.querySnapshot === null ||
      this.currentIndex >= this.querySnapshot.docs.length
    ) {
      // Fetch next batch of documents
      let q = this.query.limit(this.pageSize);
      if (this.lastDocument) {
        q = q.startAfter(this.lastDocument);
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
): AsyncGenerator<[T, U | undefined]> {
  let leftItem = await leftIterator.next();
  let rightItem = await rightIterator.next();

  while (!leftItem.done) {
    const leftKey = getLeftKey(leftItem.value);
    const rightKey = rightItem.done ? undefined : getRightKey(rightItem.value);
    // // Iterate until mismatch or end of right iterator
    // while (!rightItem.done && leftKey === rightKey) {
    //   console.log('aaaaargh 1');
    //   yield [leftItem.value, rightItem.value]; // Yield a pair with both values
    //   rightItem = await rightIterator.next();
    // }
    if (rightItem.done || leftKey < rightKey) {
      // Left item has no match or comes before the current right item
      yield [leftItem.value, undefined]; // Yield a pair with undefined for the right side
      leftItem = await leftIterator.next();
    } else if (leftKey > rightKey) {
      // Right item comes before the current left item
      rightItem = await rightIterator.next();
    } else {
      // Match found
      yield [leftItem.value, rightItem.value]; // Yield a pair with both values
      leftItem = await leftIterator.next();
      rightItem = await rightIterator.next();
    }
  }
}
