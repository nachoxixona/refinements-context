/**
 * Same rule as the backend FieldNormalizer: lower-case, strip `_`, `-` and whitespace. Used only to match a
 * search query against names the backend already normalized.
 */
export function normalizeName(name: string): string {
  return name.toLowerCase().replace(/[_\-\s]/g, '');
}

/** Same rule as the backend Names.norm: trimmed, internal whitespace collapsed, lower-case. */
export function normalizeLabel(name: string): string {
  return name.trim().replace(/\s+/g, ' ').toLowerCase();
}

/** `{prefix}:{METHOD} {path}` with path templates normalized to `{}` (backend SpecKeys.endpointKey). */
export function endpointKeyOf(prefix: string, method: string, path: string): string {
  const p = path.trim() === '' ? '/' : path.trim();
  return `${prefix}:${method.toUpperCase()} ${p.replace(/\{[^}/]*\}/g, '{}')}`;
}
