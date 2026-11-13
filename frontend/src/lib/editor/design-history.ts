import type { DesignDocument } from "@/lib/api/types";

export interface DesignHistoryState {
  past: DesignDocument[];
  present: DesignDocument;
  future: DesignDocument[];
}

const MAX_HISTORY_ENTRIES = 50;
const LOCAL_BACKUP_PREFIX = "smartcert:studio:draft:";

export function cloneDesignDocument(doc: DesignDocument): DesignDocument {
  return JSON.parse(JSON.stringify(doc)) as DesignDocument;
}

export function createHistoryState(initial: DesignDocument): DesignHistoryState {
  return {
    past: [],
    present: cloneDesignDocument(initial),
    future: [],
  };
}

export function pushHistorySnapshot(
  state: DesignHistoryState,
  nextDesign: DesignDocument
): DesignHistoryState {
  const clonedNext = cloneDesignDocument(nextDesign);
  if (JSON.stringify(state.present) === JSON.stringify(clonedNext)) {
    return state;
  }
  const nextPast = [...state.past, state.present];
  if (nextPast.length > MAX_HISTORY_ENTRIES) {
    nextPast.shift();
  }
  return {
    past: nextPast,
    present: clonedNext,
    future: [],
  };
}

export function replaceHistoryPresent(
  state: DesignHistoryState,
  nextDesign: DesignDocument
): DesignHistoryState {
  return {
    ...state,
    present: cloneDesignDocument(nextDesign),
  };
}

export function undoHistory(state: DesignHistoryState): DesignHistoryState {
  if (state.past.length === 0) {
    return state;
  }
  const previous = state.past[state.past.length - 1];
  const remainingPast = state.past.slice(0, -1);
  return {
    past: remainingPast,
    present: previous,
    future: [state.present, ...state.future].slice(0, MAX_HISTORY_ENTRIES),
  };
}

export function redoHistory(state: DesignHistoryState): DesignHistoryState {
  if (state.future.length === 0) {
    return state;
  }
  const [next, ...remainingFuture] = state.future;
  return {
    past: [...state.past, state.present].slice(-MAX_HISTORY_ENTRIES),
    present: next,
    future: remainingFuture,
  };
}

export interface LocalDraftBackup {
  templateId: string;
  baseVersion: number;
  savedAt: string;
  designJson: DesignDocument;
}

export function saveLocalDraftBackup(backup: LocalDraftBackup): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(
      `${LOCAL_BACKUP_PREFIX}${backup.templateId}`,
      JSON.stringify(backup)
    );
  } catch {
    // Ignore storage quota errors cleanly
  }
}

export function loadLocalDraftBackup(templateId: string): LocalDraftBackup | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(`${LOCAL_BACKUP_PREFIX}${templateId}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as LocalDraftBackup;
    if (!parsed?.designJson?.page || !Array.isArray(parsed?.designJson?.elements)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearLocalDraftBackup(templateId: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(`${LOCAL_BACKUP_PREFIX}${templateId}`);
  } catch {
    // Ignore storage errors cleanly
  }
}
