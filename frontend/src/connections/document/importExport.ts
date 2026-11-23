import { fetchConnectionsDocument } from '../../api/queries';
import { downloadBlob } from '../../scenario/export/download';

/** Downloads the current connections document (FR-025); returns how many dangling connections were left out. */
export async function exportConnectionsDocument(): Promise<number> {
  const doc = await fetchConnectionsDocument();
  downloadText(doc.text, 'connections.json');
  return doc.excludedDanglingConnections;
}

export function downloadText(text: string, filename: string): void {
  downloadBlob(new Blob([text], { type: 'application/json' }), filename);
}

export async function readFile(file: File): Promise<string> {
  return file.text();
}
