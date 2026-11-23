import { memo, useEffect, useRef, useState } from 'react';
import type { NodeProps } from '@xyflow/react';
import { useCreateNote, useDeleteNote, useUpdateNote } from '../api/queries';
import type { NoteKind } from '../api/types';
import { useCanvasStore } from '../canvas/canvasStore';
import type { NoteConnectionNode } from '../canvas/nodes/nodeTypes';
import { useScenarioStore } from '../scenario/scenarioStore';
import { messageOf } from '../ui/errors';
import { toast } from '../ui/toastStore';

export const NOTE_KINDS: { kind: NoteKind; label: string; icon: string }[] = [
  { kind: 'NOTE', label: 'Note', icon: '📝' },
  { kind: 'DECISION', label: 'Decision', icon: '✅' },
  { kind: 'QUESTION', label: 'Question', icon: '❓' },
];
const iconOf = (k: NoteKind) => NOTE_KINDS.find((n) => n.kind === k)?.icon ?? '📝';
const labelOf = (k: NoteKind) => NOTE_KINDS.find((n) => n.kind === k)?.label ?? 'Note';

/**
 * A sticky note (spec 004 FR-018 – FR-021): a plain note, a decision or an open question. Baseline notes are saved
 * on the server; notes written in a draft live in the scenario and baseline notes are read-only there.
 */
function NoteNodeImpl({ data }: NodeProps<NoteConnectionNode>) {
  const create = useCreateNote();
  const update = useUpdateNote();
  const del = useDeleteNote();
  const addDraftNote = useScenarioStore((s) => s.addNote);
  const updateDraftNote = useScenarioStore((s) => s.updateNote);
  const removeDraftNote = useScenarioStore((s) => s.removeNote);
  const setPending = useCanvasStore((s) => s.setPendingNote);
  const isDraft = useScenarioStore((s) => s.isDraftMode);
  const [editing, setEditing] = useState(Boolean(data.isNew));
  const [text, setText] = useState(data.text);
  const area = useRef<HTMLTextAreaElement>(null);
  const onError = (e: unknown) => toast.error(messageOf(e));

  useEffect(() => {
    if (!editing) setText(data.text);
  }, [data.text, editing]);
  useEffect(() => {
    if (editing) area.current?.focus();
  }, [editing]);

  const commit = () => {
    const value = text.trim() === '' ? '' : text;
    setEditing(false);
    if (data.isNew) {
      setPending(null);
      if (value === '') return; // an empty note is never saved
      const position = data.position ?? { x: 0, y: 0 };
      if (isDraft) addDraftNote({ text: value, kind: data.kind, position });
      else create.mutate({ text: value, kind: data.kind, position }, { onError });
      return;
    }
    if (value === '' || value === data.text || !data.noteId) {
      setText(data.text);
      return;
    }
    if (data.proposed) updateDraftNote(data.noteId, { text: value });
    else update.mutate({ id: data.noteId, patch: { text: value } }, { onError });
  };

  const setKind = (kind: NoteKind) => {
    if (data.isNew) {
      setPending({ position: data.position ?? { x: 0, y: 0 }, kind });
      return;
    }
    if (!data.noteId) return;
    if (data.proposed) updateDraftNote(data.noteId, { kind });
    else update.mutate({ id: data.noteId, patch: { kind } }, { onError });
  };

  const remove = () => {
    if (data.isNew) setPending(null);
    else if (data.noteId && data.proposed) removeDraftNote(data.noteId);
    else if (data.noteId) del.mutate(data.noteId, { onError });
  };

  const classes = ['note-node', `note-${data.kind.toLowerCase()}`];
  if (data.proposed) classes.push('note-proposed');
  if (data.readOnly) classes.push('note-readonly');
  return (
    <div className={classes.join(' ')} data-testid={`note-${data.noteId ?? 'new'}`} onDoubleClick={() => !data.readOnly && setEditing(true)}>
      <div className="note-header">
        <span aria-hidden="true">{iconOf(data.kind)}</span>
        {data.readOnly ? (
          <span className="note-kind">{labelOf(data.kind)}</span>
        ) : (
          <select aria-label="Note kind" className="note-kind nodrag" value={data.kind} onChange={(e) => setKind(e.target.value as NoteKind)}>
            {NOTE_KINDS.map((k) => (
              <option key={k.kind} value={k.kind}>
                {k.label}
              </option>
            ))}
          </select>
        )}
        {data.proposed && <span className="proposed-tag">PROPOSED</span>}
        {!data.readOnly && (
          <button type="button" className="note-delete nodrag" aria-label="Delete note" title="Delete note" onClick={remove}>
            ×
          </button>
        )}
      </div>
      {editing ? (
        <textarea
          ref={area}
          className="note-text nodrag nowheel"
          aria-label="Note text"
          value={text}
          maxLength={2000}
          rows={4}
          placeholder="Type the note…"
          onChange={(e) => setText(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === 'Escape') {
              setText(data.text);
              setEditing(false);
              if (data.isNew) setPending(null);
            } else if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) commit();
          }}
        />
      ) : (
        <div className="note-text" title={data.readOnly ? 'Baseline notes are read-only in Draft Scenario Mode' : 'Double-click to edit'}>
          {data.text}
        </div>
      )}
    </div>
  );
}

export const NoteNode = memo(NoteNodeImpl);
