/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.web.ui.common

/**
 * Opens a hidden `<input type="file">` and reads the chosen file. Plain text files are decoded as
 * UTF-8; ZIP-based archives (such as Collect Earth `.cep` project files, `.cep.zip` bundles, or
 * `.kmz` files) are decompressed in-browser via `DecompressionStream('deflate-raw')` to extract
 * their embedded `placemark.idm.xml` / `idml.xml` schemas, `project_definition.properties`, and
 * sample plot CSV grids.
 *
 * Calls back with `(fileName, text, error)`; an empty file name means the picker was cancelled.
 */
@JsFun(
  """(accept, callback) => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = accept;
  input.style.display = 'none';
  let done = false;
  const finish = (name, text, error) => {
    if (done) return;
    done = true;
    input.remove();
    callback(name, text, error);
  };
  const dec = new TextDecoder('utf-8');
  const readZipEntries = (bytes) => {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    const minEocd = Math.max(0, bytes.length - 65558);
    for (let i = bytes.length - 22; i >= minEocd; i--) {
      if (view.getUint32(i, true) === 0x06054b50) {
        eocd = i;
        break;
      }
    }
    if (eocd < 0) return [];
    const cdCount = view.getUint16(eocd + 10, true);
    let pos = view.getUint32(eocd + 16, true);
    const entries = [];
    for (let idx = 0; idx < cdCount && pos + 46 <= bytes.length; idx++) {
      if (view.getUint32(pos, true) !== 0x02014b50) break;
      const method = view.getUint16(pos + 10, true);
      const compSize = view.getUint32(pos + 20, true);
      const nameLen = view.getUint16(pos + 28, true);
      const extraLen = view.getUint16(pos + 30, true);
      const commentLen = view.getUint16(pos + 32, true);
      const localOffset = view.getUint32(pos + 42, true);
      const name = dec.decode(bytes.subarray(pos + 46, pos + 46 + nameLen));
      pos += 46 + nameLen + extraLen + commentLen;
      if (localOffset + 30 <= bytes.length && view.getUint32(localOffset, true) === 0x04034b50) {
        const lNameLen = view.getUint16(localOffset + 26, true);
        const lExtraLen = view.getUint16(localOffset + 28, true);
        const dataStart = localOffset + 30 + lNameLen + lExtraLen;
        if (dataStart + compSize <= bytes.length) {
          entries.push({ name, method, data: bytes.subarray(dataStart, dataStart + compSize) });
        }
      }
    }
    return entries;
  };
  const decompress = async (entry) => {
    if (!entry) return null;
    if (entry.method === 0) return entry.data;
    if (entry.method === 8 && typeof DecompressionStream !== 'undefined') {
      const ds = new DecompressionStream('deflate-raw');
      const writer = ds.writable.getWriter();
      writer.write(entry.data);
      writer.close();
      const ab = await new Response(ds.readable).arrayBuffer();
      return new Uint8Array(ab);
    }
    return null;
  };
  const safeComment = (s) => String(s || '').replace(/-->/g, '-- >');
  const extractCepProject = async (entries, projectFileName) => {
    const valid = entries.filter(e => !e.name.startsWith('__MACOSX/') && !e.name.endsWith('/'));
    const idmEntry =
      valid.find(e => e.name.toLowerCase().endsWith('placemark.idm.xml')) ||
      valid.find(e => e.name.toLowerCase().endsWith('.idm.xml')) ||
      valid.find(e => e.name.toLowerCase() === 'idml.xml' || e.name.toLowerCase().endsWith('/idml.xml'));
    if (!idmEntry) return null;
    const idmBytes = await decompress(idmEntry);
    if (!idmBytes) return null;
    const idmXml = dec.decode(idmBytes);
    const propsEntry = valid.find(e => e.name.toLowerCase().endsWith('project_definition.properties'));
    const propsText = propsEntry ? dec.decode((await decompress(propsEntry)) || new Uint8Array(0)) : '';
    let csvEntry = null;
    const csvMatch = propsText.match(/^csv\s*=\s*(.+)$/m);
    if (csvMatch) {
      const csvRel = csvMatch[1].trim().replace(/^\$\{project_path\}\/?/, '');
      csvEntry = valid.find(e => e.name === csvRel || e.name.endsWith('/' + csvRel));
    }
    if (!csvEntry) {
      csvEntry =
        valid.find(e => e.name.toLowerCase().startsWith('grid/') && e.name.toLowerCase().endsWith('.csv')) ||
        valid.find(e => e.name.toLowerCase().endsWith('.csv') || e.name.toLowerCase().endsWith('.ced'));
    }
    let csvPreview = '';
    if (csvEntry) {
      const csvBytes = await decompress(csvEntry);
      if (csvBytes) {
        const fullCsv = dec.decode(csvBytes);
        csvPreview = fullCsv.split(/\r?\n/).slice(0, 155).join('\n');
      }
    }
    const baseName = projectFileName.split('/').pop() || projectFileName;
    return '<!--COLLECT_EARTH_FILE_NAME:' + safeComment(baseName) + '-->\n' +
      '<!--COLLECT_EARTH_PROPERTIES\n' + safeComment(propsText) + '\n-->\n' +
      '<!--COLLECT_EARTH_PLOT_CSV\n' + safeComment(csvPreview) + '\n-->\n' +
      idmXml;
  };
  const extractText = async (fileName, ab) => {
    const u8 = new Uint8Array(ab);
    const isZip = u8.length >= 4 && u8[0] === 0x50 && u8[1] === 0x4b && u8[2] === 0x03 && u8[3] === 0x04;
    if (!isZip) return dec.decode(u8);
    const topEntries = readZipEntries(u8);
    const directProject = await extractCepProject(topEntries, fileName);
    if (directProject) return directProject;
    const cepEntries = topEntries.filter(e => !e.name.startsWith('__MACOSX/') && e.name.toLowerCase().endsWith('.cep'));
    const projects = [];
    for (const cepEntry of cepEntries) {
      const cepBytes = await decompress(cepEntry);
      if (cepBytes) {
        const innerEntries = readZipEntries(cepBytes);
        const proj = await extractCepProject(innerEntries, cepEntry.name);
        if (proj) projects.push(proj);
      }
    }
    if (projects.length > 0) return projects.join('\n<!--COLLECT_EARTH_PROJECT_SEPARATOR-->\n');
    const zipEntries = topEntries.filter(e => !e.name.startsWith('__MACOSX/') && e.name.toLowerCase().endsWith('.zip'));
    for (const zEntry of zipEntries) {
      const zBytes = await decompress(zEntry);
      if (zBytes) {
        const innerEntries = readZipEntries(zBytes);
        const proj = await extractCepProject(innerEntries, zEntry.name);
        if (proj) projects.push(proj);
      }
    }
    if (projects.length > 0) return projects.join('\n<!--COLLECT_EARTH_PROJECT_SEPARATOR-->\n');
    const fallbackEntry = topEntries.find(e =>
      !e.name.startsWith('__MACOSX/') && /\.(xml|xhtml|kml|geojson|csv)$/i.test(e.name)
    );
    if (fallbackEntry) {
      const fbBytes = await decompress(fallbackEntry);
      if (fbBytes) return dec.decode(fbBytes);
    }
    return '';
  };
  input.addEventListener('change', async () => {
    const file = input.files && input.files[0];
    if (!file) { finish('', '', ''); return; }
    try {
      const ab = await file.arrayBuffer();
      const text = await extractText(file.name, ab);
      finish(file.name, String(text || ''), '');
    } catch (e) {
      finish(file.name, '', 'Couldn\'t read ' + file.name + '. Try again.');
    }
  });
  input.addEventListener('cancel', () => finish('', '', ''));
  document.body.appendChild(input);
  input.click();
}"""
)
private external fun jsPickTextFile(accept: String, callback: (String, String, String) -> Unit)

internal actual val isPlatformTextFilePickerAvailable: Boolean = true

actual fun openPlatformTextFilePicker(
  accept: String,
  onResult: (TextFilePickResult) -> Unit,
) {
  jsPickTextFile(accept) { fileName, text, error ->
    onResult(
      when {
        error.isNotEmpty() -> TextFilePickResult.Failed(error)
        fileName.isEmpty() -> TextFilePickResult.Cancelled
        else -> TextFilePickResult.Picked(fileName, text)
      }
    )
  }
}
