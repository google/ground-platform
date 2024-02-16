import { compileFromFile } from 'json-schema-to-typescript'
import fs from 'fs';

// compile from file
compileFromFile('./person.json')
  .then(ts => fs.writeFileSync('person.d.ts', ts))