/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Runs the Collect One local database for SQLDelight's web worker driver
// (app.cash.sqldelight.driver.worker.WebWorkerDriver), with the official SQLite WebAssembly build.
//
// Storage, in order of preference (docs/technical/client/local-data-store.md):
// 1. The Origin Private File System, through SQLite's "opfs-sahpool" VFS. SQLite writes only the
//    pages a transaction changes, and needs no cross-origin isolation headers.
// 2. When the browser has no OPFS (older browsers, some private modes): an in-memory database whose
//    bytes are saved to IndexedDB after every committed write.
// 3. When OPFS exists but another tab holds the database: an in-memory database that isn't saved,
//    so the two tabs can't overwrite each other's data.
//
// Protocol (the same as SQLDelight's sql.js worker): each message has an `id` and an `action`
// ("exec" with `sql` and `params`, "begin_transaction", "end_transaction", or
// "rollback_transaction"). The reply has the same `id` and either `results: { values: rows }` or
// `error`. Messages are handled one at a time, in order; a write is stored before it is answered.

import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

const DATABASE_FILE = '/collect-one.sqlite3';
const SAH_POOL_NAME = 'collect-one';
const INDEXED_DB_NAME = 'collect-one-local-database';
const INDEXED_DB_STORE = 'files';

/** Saves the in-memory database's bytes in IndexedDB. */
class IndexedDbFile {
  static async open() {
    const db = await new Promise((resolve, reject) => {
      const request = indexedDB.open(INDEXED_DB_NAME, 1);
      request.onupgradeneeded = () => request.result.createObjectStore(INDEXED_DB_STORE);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    return new IndexedDbFile(db);
  }

  constructor(db) {
    this.db = db;
  }

  read() {
    return this.request('readonly', (store) => store.get(DATABASE_FILE));
  }

  write(bytes) {
    return this.request('readwrite', (store) => store.put(bytes, DATABASE_FILE));
  }

  request(mode, makeRequest) {
    return new Promise((resolve, reject) => {
      const transaction = this.db.transaction(INDEXED_DB_STORE, mode);
      const request = makeRequest(transaction.objectStore(INDEXED_DB_STORE));
      transaction.oncomplete = () => resolve(request.result);
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  }
}

async function openDatabase() {
  const sqlite3 = await sqlite3InitModule();
  if (typeof navigator !== 'undefined' && navigator.storage && navigator.storage.getDirectory) {
    try {
      const pool = await sqlite3.installOpfsSAHPoolVfs({ name: SAH_POOL_NAME });
      return { db: new pool.OpfsSAHPoolDb(DATABASE_FILE), saveCommitted: null };
    } catch (e) {
      console.warn(
        'The local database is open in another tab; this tab keeps its changes in memory only.',
        e
      );
      return { db: new sqlite3.oo1.DB(':memory:'), saveCommitted: null };
    }
  }
  const file = await IndexedDbFile.open();
  const db = new sqlite3.oo1.DB(':memory:');
  const saved = await file.read();
  if (saved && saved.byteLength > 0) {
    const pointer = sqlite3.wasm.allocFromTypedArray(saved);
    db.checkRc(
      sqlite3.capi.sqlite3_deserialize(
        db.pointer,
        'main',
        pointer,
        saved.byteLength,
        saved.byteLength,
        sqlite3.capi.SQLITE_DESERIALIZE_FREEONCLOSE | sqlite3.capi.SQLITE_DESERIALIZE_RESIZEABLE
      )
    );
  }
  return { db, saveCommitted: () => file.write(sqlite3.capi.sqlite3_js_db_export(db)) };
}

const opened = openDatabase();
let inTransaction = false;

/** Statements that never change the database, so nothing needs saving after them. */
function isReadOnly(sql) {
  return /^\s*(select|pragma\s+\w+\s*;?\s*$)/i.test(sql);
}

async function handle(data) {
  const { db, saveCommitted } = await opened;
  switch (data && data.action) {
    case 'exec': {
      if (!data.sql) throw new Error('exec: Missing query string');
      const rows = [];
      const options = { sql: data.sql, rowMode: 'array', resultRows: rows };
      if (data.params && data.params.length > 0) options.bind = data.params;
      db.exec(options);
      if (saveCommitted && !inTransaction && !isReadOnly(data.sql)) await saveCommitted();
      return { values: rows };
    }
    case 'begin_transaction':
      db.exec('BEGIN TRANSACTION;');
      inTransaction = true;
      return { values: [] };
    case 'end_transaction':
      db.exec('END TRANSACTION;');
      inTransaction = false;
      if (saveCommitted) await saveCommitted();
      return { values: [] };
    case 'rollback_transaction':
      db.exec('ROLLBACK TRANSACTION;');
      inTransaction = false;
      return { values: [] };
    default:
      throw new Error(`Unsupported action: ${data && data.action}`);
  }
}

let queue = Promise.resolve();
self.onmessage = (event) => {
  const data = event.data;
  queue = queue.then(() =>
    handle(data).then(
      (results) => postMessage({ id: data.id, results }),
      (error) => postMessage({ id: data.id, error: { message: String(error && error.message), name: error && error.name } })
    )
  );
};
