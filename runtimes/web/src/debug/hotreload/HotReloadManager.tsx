/**
 * Hot Reload Manager for IR JSON files.
 *
 * Enables live updates of SDUI components during development by:
 * - Polling a server endpoint (network-based)
 * - Manual reload triggers
 *
 * Mirrors the Android HotReloadManager functionality.
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import type { IRDocument } from '../../core/ir/IRModels';

interface HotReloadState {
  isWatching: boolean;
  reloadCount: number;
  lastReloadTime: number;
  lastError: string | null;
}

// Default configuration
const DEFAULT_POLL_INTERVAL = 2000; // 2 seconds
const IR_ENDPOINT = '/ir-components.json';

/**
 * Hot reload hook for component updates.
 */
export function useHotReload(
  onDocumentUpdate: (doc: IRDocument) => void,
  options: { pollInterval?: number; enabled?: boolean } = {}
) {
  const { pollInterval = DEFAULT_POLL_INTERVAL, enabled = true } = options;

  const [state, setState] = useState<HotReloadState>({
    isWatching: false,
    reloadCount: 0,
    lastReloadTime: 0,
    lastError: null,
  });

  const lastEtagRef = useRef<string | null>(null);
  const intervalRef = useRef<number | null>(null);

  const fetchDocument = useCallback(async (forceReload = false) => {
    try {
      const headers: HeadersInit = {};

      // Use ETag for efficient polling
      if (!forceReload && lastEtagRef.current) {
        headers['If-None-Match'] = lastEtagRef.current;
      }

      const response = await fetch(IR_ENDPOINT, { headers });

      // Not modified
      if (response.status === 304) {
        return;
      }

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      // Update ETag
      const etag = response.headers.get('ETag');
      if (etag) {
        lastEtagRef.current = etag;
      }

      const doc = await response.json() as IRDocument;
      onDocumentUpdate(doc);

      setState((prev) => ({
        ...prev,
        reloadCount: prev.reloadCount + 1,
        lastReloadTime: Date.now(),
        lastError: null,
      }));
    } catch (err) {
      setState((prev) => ({
        ...prev,
        lastError: err instanceof Error ? err.message : 'Unknown error',
      }));
    }
  }, [onDocumentUpdate]);

  // Start/stop polling
  useEffect(() => {
    if (!enabled) {
      setState((prev) => ({ ...prev, isWatching: false }));
      return;
    }

    setState((prev) => ({ ...prev, isWatching: true }));

    // Initial fetch
    fetchDocument(true);

    // Start polling
    intervalRef.current = window.setInterval(() => {
      fetchDocument();
    }, pollInterval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      setState((prev) => ({ ...prev, isWatching: false }));
    };
  }, [enabled, pollInterval, fetchDocument]);

  const reload = useCallback(() => {
    fetchDocument(true);
  }, [fetchDocument]);

  return {
    ...state,
    reload,
  };
}

// (A6#13) the `HotReloadStatus` indicator component that stood here was never
// mounted by anything — the harness renders its own status chrome — so the
// hook below is the whole live surface of this module.

export default useHotReload;
