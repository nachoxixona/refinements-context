// Prepends a "generated, do not edit" header to the openapi-typescript output (T018).
import { readFileSync, writeFileSync } from 'node:fs';

const file = process.argv[2];
const header =
  '/**\n * GENERATED FILE - DO NOT EDIT.\n * Source: specs/002-architecture-visualizer/contracts/openapi.yaml\n * Regenerate with `npm run gen:api`.\n */\n';
const body = readFileSync(file, 'utf8');
writeFileSync(file, body.startsWith('/**\n * GENERATED') ? body : header + body);
