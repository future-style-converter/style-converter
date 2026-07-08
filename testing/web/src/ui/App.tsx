/**
 * Main App Component
 *
 * Entry point for the SDUI web testing application.
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import type { IRDocument } from '../style/core/ir/IRModels';
import { ComponentGallery } from './ComponentGallery';
import { CaptureGallery } from './CaptureGallery';
import { FixtureCanvas } from './FixtureCanvas';
import { useHotReload } from '../style/debug/hotreload/HotReloadManager';

const IR_ASSET_PATH = '/ir-components.json';

/**
 * Returns true when the URL contains `?mode=capture` — the chromeless
 * render used by the Puppeteer capture script. Parsed once per mount,
 * and doesn't change during a session.
 */
function isCaptureMode(): boolean {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).get('mode') === 'capture';
}

/**
 * Returns true when the URL contains `?wpt=1` — the WPT capture mode that
 * suppresses placeholder text overlays (see ComponentRenderer's WPT_MODE)
 * AND opts every SDUI-rendered element into `box-sizing: content-box` via
 * the `body.wpt-mode` selector in index.html. Both signals key off the same
 * URL param; parsed once per mount.
 */
function isWptMode(): boolean {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).get('wpt') === '1';
}

/**
 * Returns the `?fixture=<name>` URL parameter when the page is being
 * driven by the Tier 5 interaction-state harness, or null otherwise.
 *
 * In fixture mode the page renders a single component (loaded from
 * /fixtures/<name>.json) inside a <FixtureCanvas> wrapper that
 * Puppeteer can grab via `[data-testid="<name>"]`. See
 * testing/web/src/ui/FixtureCanvas.tsx for the wrapper contract and
 * testing/TIER5_PLAN.md for the full pipeline.
 */
function getFixtureName(): string | null {
  if (typeof window === 'undefined') return null;
  const v = new URLSearchParams(window.location.search).get('fixture');
  // Reject empty / whitespace-only values: `?fixture=` should fall through
  // to the regular gallery rather than mounting a "Component '' not found"
  // FixtureCanvas error state.
  return v && v.trim() ? v.trim() : null;
}

export function App() {
  const [document, setDocument] = useState<IRDocument | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [captureMode] = useState(isCaptureMode);
  // Parse `?fixture=<name>` once per mount. Same lifecycle as captureMode:
  // both are URL-driven test harness modes that bypass the gallery entirely.
  const [fixtureName] = useState(getFixtureName);
  // Parse `?wpt=1` once per mount. Drives the `wpt-mode` body class so the
  // index.html `body.wpt-mode [data-component-id], ... { box-sizing:
  // content-box }` override restores the WPT spec's assumed default box-
  // sizing model on SDUI elements only (F-G-HARNESS swarm-003 Bug 1).
  const [wptMode] = useState(isWptMode);

  // Toggle the body class once, before the first render, so the chromeless
  // capture styles apply to #root and body without a flash of the gallery
  // frame. The class is set/unset synchronously from this effect.
  // Fixture mode reuses the same chromeless body class as capture mode —
  // both want a black-background full-bleed canvas with no gallery scaffolding.
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (captureMode || fixtureName) {
      window.document.body.classList.add('capture-mode');
      return () => {
        window.document.body.classList.remove('capture-mode');
      };
    }
  }, [captureMode, fixtureName]);

  // Toggle the `wpt-mode` body class independently of capture-mode. The
  // class governs the index.html `[data-component-id]` box-sizing override
  // (Bug 1 from F-G-HARNESS swarm-003). Kept separate so a future test
  // harness could opt into WPT semantics without entering capture mode (or
  // vice versa) without code change.
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (wptMode) {
      window.document.body.classList.add('wpt-mode');
      return () => {
        window.document.body.classList.remove('wpt-mode');
      };
    }
  }, [wptMode]);

  // Load document from assets.
  //
  // In fixture mode we load `/fixtures/<name>.json` (a single-component IR
  // doc pre-converted by `npm run build-fixtures`); otherwise we load the
  // shared `/ir-components.json` that the gallery + capture mode use. The
  // fetch shape is identical, so the rest of the component stays the same.
  const loadDocument = useCallback(async () => {
    try {
      const path = fixtureName ? `/fixtures/${fixtureName}.json` : IR_ASSET_PATH;
      const response = await fetch(path);
      if (!response.ok) {
        throw new Error(`Failed to load ${path}: ${response.status}`);
      }
      const data = await response.json() as IRDocument;
      setDocument(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load IR document');
      console.error('Load error:', err);
    }
  }, [fixtureName]);

  // Initial load
  useEffect(() => {
    loadDocument();
  }, [loadDocument]);

  // Hot reload support.
  //
  // Two things we handle here to keep the capture-mode console quiet:
  //
  //   1. Disable polling entirely in capture mode — there's no gallery to
  //      update and Puppeteer doesn't need the IR re-fetched every 2s.
  //   2. Memoize both the callback and the options object. `useHotReload`'s
  //      internal useCallback depends on the `onDocumentUpdate` callback
  //      identity; passing an inline lambda creates a new function ref on
  //      every render, which triggers the internal effect, which calls
  //      setState, which re-renders, which → infinite loop and React's
  //      "Maximum update depth exceeded" warning.
  const onDocumentUpdate = useCallback((doc: IRDocument) => {
    setDocument(doc);
  }, []);
  // Hot reload is also disabled in fixture mode: the interaction-state
  // harness wants a stable single-render so a state-event-then-screenshot
  // cycle isn't disturbed by a background refetch swapping the IR.
  const hotReloadOpts = useMemo(
    () => ({ enabled: !captureMode && !fixtureName }),
    [captureMode, fixtureName]
  );
  useHotReload(onDocumentUpdate, hotReloadOpts);

  // Fixture mode: render exactly one component (looked up by name) inside
  // a FixtureCanvas with the data-testid + tabIndex + ready-sentinel
  // contract that interaction-states.mjs expects. See FixtureCanvas.tsx
  // and testing/TIER5_PLAN.md.
  if (fixtureName) {
    if (error) {
      return <pre style={styles.errorPlain}>{error}</pre>;
    }
    if (!document) {
      return <div data-fixture-loading="true" style={{ display: 'none' }} />;
    }
    return <FixtureCanvas document={document} fixtureName={fixtureName} />;
  }

  // Capture mode bypasses the gallery entirely: no chrome, no search, no
  // pagination — a flat vertical list of chromeless canvases, ready for
  // Puppeteer to element-screenshot one by one.
  if (captureMode) {
    if (error) {
      return <pre style={styles.errorPlain}>{error}</pre>;
    }
    if (!document) {
      return <div data-capture-loading="true" style={{ display: 'none' }} />;
    }
    return <CaptureGallery document={document} />;
  }

  return (
    <div style={styles.app}>
      <header style={styles.header}>
        <div style={styles.headerTopRow}>
          <div style={styles.headerLeft}>
            <h1 style={styles.title}>Style Converter</h1>
            <span style={styles.subtitle}>Web Testing</span>
          </div>
        </div>
        <input
          type="text"
          placeholder="Search components..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={styles.searchInput}
        />
      </header>

      <main style={styles.main}>
        {error ? (
          <div style={styles.error}>
            <h3>Error Loading Document</h3>
            <p>{error}</p>
            <button onClick={loadDocument} style={styles.retryButton}>
              Retry
            </button>
          </div>
        ) : (
          <ComponentGallery document={document} searchQuery={searchQuery} />
        )}
      </main>
    </div>
  );
}

/**
 * Styles
 */
const styles: Record<string, React.CSSProperties> = {
  app: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    padding: '12px',
    background: 'rgba(0,0,0,0.3)',
    borderBottom: '1px solid rgba(255,255,255,0.1)',
  },
  headerTopRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'baseline',
    gap: '12px',
  },
  title: {
    margin: 0,
    fontSize: '20px',
    fontWeight: 600,
    color: '#fff',
  },
  subtitle: {
    color: '#888',
    fontSize: '14px',
  },
  searchInput: {
    width: '100%',
    padding: '8px 12px',
    background: 'rgba(255,255,255,0.05)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '6px',
    color: '#fff',
    fontSize: '14px',
    outline: 'none',
  },
  main: {
    flex: 1,
    overflow: 'auto',
  },
  error: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '48px',
    color: '#f87171',
    textAlign: 'center',
  },
  errorPlain: {
    color: '#f87171',
    padding: '16px',
    margin: 0,
    fontFamily: 'monospace',
  },
  retryButton: {
    marginTop: '16px',
    padding: '8px 24px',
    background: 'rgba(239, 68, 68, 0.2)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    borderRadius: '6px',
    color: '#f87171',
    fontSize: '14px',
    cursor: 'pointer',
  },
};

export default App;
