import { fetchScenarioExport } from '../../api/queries';
import type { ScenarioDocument } from '../../api/types';
import { useScenarioStore } from '../scenarioStore';
import { downloadBlob, safeFileName } from './download';

/**
 * Downloads `<name>.scenario.json` (FR-041, formatVersion 2). A saved scenario without unsaved changes uses
 * the server export; an unsaved or dirty draft is serialized from the store.
 */
export async function exportScenarioJson(): Promise<void> {
  const s = useScenarioStore.getState();
  const doc: ScenarioDocument = s.scenarioId && !s.dirty ? await fetchScenarioExport(s.scenarioId) : s.toScenarioDocument();
  downloadBlob(new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' }), `${safeFileName(doc.name)}.scenario.json`);
}
