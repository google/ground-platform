const fs = require('fs');
const path = require('path');
const { compileFromFile } = require('json-schema-to-typescript');

const JsonPath = path.resolve(__dirname, './schema/com/google/ground/shared/schema/loi/loi-document.schema.json');

(async function () {
  try {
    fs.writeFileSync('schemas/person.d.ts', await compileFromFile(JsonPath))
  } catch (e) {
    console.log(e);
    process.exit(1);
  }
})();