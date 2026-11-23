import { useEffect, useMemo, useRef, useState } from 'react';
import CodeMirror, { EditorView } from '@uiw/react-codemirror';
import { json, jsonParseLinter } from '@codemirror/lang-json';
import { linter, lintGutter, type Diagnostic } from '@codemirror/lint';
import { fetchConnectionsDocument } from '../../api/queries';
import type { DocumentError } from '../../api/types';
import { ConfirmDialog, Modal } from '../../ui/Modal';
import { toast } from '../../ui/toastStore';
import { messageOf } from '../../ui/errors';
import { describe, useApplyDocument } from './useApplyDocument';
import { readFile } from './importExport';

/** Character offset of a 1-based line and column in `text`. */
export function offsetOf(text: string, line: number, column: number): number {
  let offset = 0;
  for (let l = 1; l < line; l++) {
    const nl = text.indexOf('\n', offset);
    if (nl < 0) return text.length;
    offset = nl + 1;
  }
  return Math.min(text.length, offset + Math.max(0, column - 1));
}

/** Backend errors as editor diagnostics, one line-long mark each (research R6). */
export function diagnostics(text: string, errors: readonly DocumentError[]): Diagnostic[] {
  return errors.map((e) => {
    const from = e.line ? offsetOf(text, e.line, e.column ?? 1) : 0;
    const lineEnd = text.indexOf('\n', from);
    return { from, to: lineEnd < 0 ? text.length : Math.max(from + 1, lineEnd), severity: 'error', message: e.message };
  });
}

/**
 * The in-app connections document editor (FR-022, research R12). The buffer lives in memory only; Save runs the
 * same validation and preview as an upload, and backend errors appear as inline markers.
 */
export function ConnectionsDocumentEditor({ onClose }: { onClose: () => void }) {
  const [original, setOriginal] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [errors, setErrors] = useState<DocumentError[]>([]);
  const [confirmClose, setConfirmClose] = useState(false);
  const errorsFor = useRef<string>('');
  const file = useRef<HTMLInputElement>(null);

  const load = async () => {
    try {
      const doc = await fetchConnectionsDocument();
      setOriginal(doc.text);
      setText(doc.text);
      setErrors([]);
      if (doc.excludedDanglingConnections > 0) {
        toast.info(`${doc.excludedDanglingConnections} dangling connection(s) are not in the document; resolve them in the Dangling panel.`);
      }
    } catch (e) {
      toast.error(messageOf(e));
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const { start, dialog } = useApplyDocument({
    onApplied: () => void load(),
    onInvalid: (errs) => {
      errorsFor.current = text;
      setErrors(errs);
    },
    onStale: () => void load(),
  });

  const backendLint = useMemo(
    () => linter((view) => (view.state.doc.toString() === errorsFor.current ? diagnostics(errorsFor.current, errors) : []), { delay: 0 }),
    [errors],
  );
  const extensions = useMemo(() => [json(), linter(jsonParseLinter()), backendLint, lintGutter(), EditorView.lineWrapping], [backendLint]);
  const dirty = original !== null && text !== original;

  const close = () => (dirty ? setConfirmClose(true) : onClose());

  return (
    <Modal
      title="Connections document"
      onClose={close}
      wide
      footer={
        <>
          <button type="button" onClick={() => file.current?.click()}>
            Upload file…
          </button>
          <input
            ref={file}
            type="file"
            accept="application/json,.json"
            hidden
            aria-label="Upload connections document"
            onChange={async (e) => {
              const f = e.target.files?.[0];
              if (f) setText(await readFile(f));
              e.target.value = '';
            }}
          />
          <span className="grow" />
          <button type="button" disabled={!dirty} onClick={() => original !== null && setText(original)}>
            Discard changes
          </button>
          <button type="button" className="primary" disabled={original === null} onClick={() => void start(text)}>
            Save…
          </button>
        </>
      }
    >
      <p className="muted">
        One entry per connection: <code>name</code>, <code>source</code>, <code>target</code>, optional <code>kind</code> ("request" or
        "event") and <code>description</code>. Saving replaces all connections after you confirm the preview.
      </p>
      <div className="doc-editor" data-testid="connections-editor">
        <CodeMirror value={text} height="420px" extensions={extensions} onChange={(v) => setText(v)} aria-label="Connections document" basicSetup={{ foldGutter: true }} />
      </div>
      {errors.length > 0 && text === errorsFor.current && (
        <div className="form-error" role="alert">
          <p>
            {errors.length} error{errors.length === 1 ? '' : 's'}; nothing was applied:
          </p>
          <ul>
            {errors.map((e, i) => (
              <li key={i}>{describe(e)}</li>
            ))}
          </ul>
        </div>
      )}
      {dialog}
      {confirmClose && (
        <ConfirmDialog
          title="Unsaved changes"
          message={<p>The connections document has unsaved changes. Discard them?</p>}
          confirmLabel="Discard"
          danger
          onCancel={() => setConfirmClose(false)}
          onConfirm={onClose}
        />
      )}
    </Modal>
  );
}
