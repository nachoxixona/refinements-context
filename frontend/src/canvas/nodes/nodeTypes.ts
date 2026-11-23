import type { Node } from '@xyflow/react';
import type { Direction } from '../search/fieldSearch';
import type { CardView, MicroserviceView } from '../viewModel';

export type NodeHighlight = 'none' | 'selected' | 'upstream' | 'downstream' | 'both' | 'search' | 'dimmed';

type UiExtras = {
  highlight: NodeHighlight;
  draftMode: boolean;
};

export type MicroserviceUiData = MicroserviceView & UiExtras & {
  /** baseline connections that reference it, for the delete warning (FR-016) */
  connectionCount: number;
};

export type CardUiData = CardView &
  UiExtras & {
    /** `${endpointKey}|${fieldPath}` → IN/OUT, for search hits */
    searchFields?: ReadonlyMap<string, Direction>;
  };

export type MicroserviceConnectionNode = Node<MicroserviceUiData, 'microservice'>;
export type CardConnectionNode = Node<CardUiData, 'specCard'>;
/** A sticky note (spec 004 FR-018): a baseline note, a draft note, or one being written (`isNew`). */
export type NoteUiData = {
  /** baseline note id, or the draft note's tempKey; null while new */
  noteId: string | null;
  text: string;
  kind: import('../../api/types').NoteKind;
  /** added in the current draft (FR-020) */
  proposed: boolean;
  /** a baseline note seen in draft mode */
  readOnly: boolean;
  isNew?: boolean;
  /** where a new note is created */
  position?: { x: number; y: number };
};

export type NoteConnectionNode = Node<NoteUiData, 'note'>;
export type CanvasConnectionNode = MicroserviceConnectionNode | CardConnectionNode | NoteConnectionNode;
