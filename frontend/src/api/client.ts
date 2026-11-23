import type { paths } from './schema';
import type { DocumentError, ParseError } from './types';

/** An RFC 9457 Problem Details error from the backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string | undefined;
  /** Spec parse errors (422 on import/sync). */
  readonly errors: ParseError[];
  /** Connections document errors (422 on preview/apply), with positions. */
  readonly documentErrors: DocumentError[];
  readonly conflict: string | undefined;
  readonly existingCardId: string | undefined;

  constructor(status: number, problem: Record<string, unknown>) {
    const title = typeof problem.title === 'string' ? problem.title : `HTTP ${status}`;
    const detail = typeof problem.detail === 'string' ? problem.detail : undefined;
    super(detail ?? title);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    const errors = Array.isArray(problem.errors) ? (problem.errors as Record<string, unknown>[]) : [];
    const isDocument = errors.some((e) => typeof e.kind === 'string');
    this.errors = isDocument ? [] : (errors as unknown as ParseError[]);
    this.documentErrors = isDocument ? (errors as unknown as DocumentError[]) : [];
    this.conflict = typeof problem.conflict === 'string' ? problem.conflict : undefined;
    this.existingCardId = typeof problem.existingCardId === 'string' ? problem.existingCardId : undefined;
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
/** Contract paths (without the `/api` server prefix), so a typo in a path is a compile error. */
export type ApiPath = keyof paths;

/** Fills `{param}` placeholders, URL-encoding each value. */
export function buildPath(path: ApiPath, params: Record<string, string> = {}): string {
  return path.replace(/\{(\w+)\}/g, (_, name: string) => {
    const value = params[name];
    if (value === undefined) throw new Error(`Missing path parameter ${name}`);
    return encodeURIComponent(value);
  });
}

export async function request<T>(
  method: Method,
  path: ApiPath,
  options: { params?: Record<string, string>; query?: Record<string, string | undefined>; body?: unknown } = {},
): Promise<T> {
  let url = '/api' + buildPath(path, options.params);
  if (options.query) {
    const q = new URLSearchParams();
    for (const [k, v] of Object.entries(options.query)) if (v !== undefined) q.set(k, v);
    const qs = q.toString();
    if (qs) url += '?' + qs;
  }
  const res = await fetch(url, {
    method,
    headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const text = await res.text();
  const parsed: unknown = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, (parsed ?? {}) as Record<string, unknown>);
  }
  return parsed as T;
}
