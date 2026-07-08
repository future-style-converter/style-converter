/**
 * Component Gallery - Displays all IR components in a scrollable list.
 */

import React, { useMemo, useState } from 'react';
import type { IRComponent, IRDocument } from '../style/core/ir/IRModels';
import { ComponentRenderer } from '../sdui/ComponentRenderer';

interface ComponentGalleryProps {
  document: IRDocument | null;
  searchQuery?: string;
}

/**
 * Flatten nested components for display.
 */
function flattenComponents(components: IRComponent[]): IRComponent[] {
  const result: IRComponent[] = [];

  function traverse(component: IRComponent) {
    result.push(component);
    if (component.children) {
      component.children.forEach(traverse);
    }
  }

  components.forEach(traverse);
  return result;
}

const PAGE_SIZE = 50;

/**
 * Main gallery component.
 */
export function ComponentGallery({ document, searchQuery = '' }: ComponentGalleryProps) {
  const [showNested, setShowNested] = useState(true);
  const [page, setPage] = useState(0);

  const allComponents = useMemo(() => {
    if (!document) return [];

    const components = showNested
      ? flattenComponents(document.components)
      : document.components;

    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      return components.filter(
        (c) =>
          c.name.toLowerCase().includes(query) ||
          c.id.toLowerCase().includes(query)
      );
    }

    return components;
  }, [document, searchQuery, showNested]);

  // Reset page when search changes
  React.useEffect(() => {
    setPage(0);
  }, [searchQuery, showNested]);

  const totalPages = Math.ceil(allComponents.length / PAGE_SIZE);
  const components = allComponents.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  if (!document) {
    return (
      <div style={styles.loading}>
        Loading IR document...
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <div style={styles.stats}>
          <span style={styles.statItem}>
            <strong>{document.components.length}</strong> top-level
          </span>
          <span style={styles.statItem}>
            <strong>{allComponents.length}</strong> {showNested ? 'total' : 'filtered'}
          </span>
        </div>
        <label style={styles.toggle}>
          <input
            type="checkbox"
            checked={showNested}
            onChange={(e) => setShowNested(e.target.checked)}
          />
          Include nested
        </label>
      </div>

      {/* Pagination controls */}
      <div style={styles.pagination}>
        <button
          style={styles.pageButton}
          onClick={() => setPage(0)}
          disabled={page === 0}
        >
          First
        </button>
        <button
          style={styles.pageButton}
          onClick={() => setPage(p => Math.max(0, p - 1))}
          disabled={page === 0}
        >
          Prev
        </button>
        <span style={styles.pageInfo}>
          Page {page + 1} of {totalPages} ({page * PAGE_SIZE + 1}-{Math.min((page + 1) * PAGE_SIZE, allComponents.length)} of {allComponents.length})
        </span>
        <button
          style={styles.pageButton}
          onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
          disabled={page >= totalPages - 1}
        >
          Next
        </button>
        <button
          style={styles.pageButton}
          onClick={() => setPage(totalPages - 1)}
          disabled={page >= totalPages - 1}
        >
          Last
        </button>
      </div>

      <div style={styles.grid}>
        {components.map((component, index) => (
          <ComponentCard key={component.id || index} component={component} index={page * PAGE_SIZE + index} />
        ))}
      </div>
    </div>
  );
}

/**
 * Individual component card.
 */
interface ComponentCardProps {
  component: IRComponent;
  index: number;
}

function ComponentCard({ component, index }: ComponentCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <div style={styles.card}>
      <div style={styles.cardHeader}>
        <span style={styles.cardIndex}>#{index + 1}</span>
        <span style={styles.cardName}>{component.name}</span>
        <span style={styles.cardId}>{component.id}</span>
      </div>

      <div style={styles.cardContent}>
        <ComponentRenderer component={component} />
      </div>

      <div style={styles.cardFooter}>
        <span style={styles.propCount}>
          {component.properties.length} props
          {component.children ? `, ${component.children.length} children` : ''}
        </span>
        <button
          style={styles.expandButton}
          onClick={() => setIsExpanded(!isExpanded)}
        >
          {isExpanded ? 'Hide Props' : 'Show Props'}
        </button>
      </div>

      {isExpanded && (
        <div style={styles.propsPanel}>
          <pre style={styles.propsCode}>
            {JSON.stringify(component.properties, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
}

/**
 * Styles object.
 */
const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: '16px',
  },
  loading: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: '200px',
    color: '#888',
    fontSize: '16px',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '16px',
    padding: '12px',
    background: 'rgba(255,255,255,0.05)',
    borderRadius: '8px',
  },
  stats: {
    display: 'flex',
    gap: '16px',
  },
  statItem: {
    color: '#aaa',
    fontSize: '14px',
  },
  toggle: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    cursor: 'pointer',
    color: '#aaa',
    fontSize: '14px',
  },
  pagination: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '8px',
    marginBottom: '16px',
    padding: '12px',
    background: 'rgba(255,255,255,0.03)',
    borderRadius: '8px',
  },
  pageButton: {
    padding: '6px 12px',
    background: 'rgba(255,255,255,0.1)',
    border: '1px solid rgba(255,255,255,0.2)',
    borderRadius: '4px',
    color: '#aaa',
    fontSize: '12px',
    cursor: 'pointer',
  },
  pageInfo: {
    color: '#888',
    fontSize: '13px',
    padding: '0 12px',
  },
  grid: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  card: {
    background: 'rgba(255,255,255,0.03)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '8px',
    overflow: 'hidden',
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '8px 12px',
    background: 'rgba(255,255,255,0.05)',
    borderBottom: '1px solid rgba(255,255,255,0.1)',
  },
  cardIndex: {
    color: '#666',
    fontSize: '12px',
    fontFamily: 'monospace',
  },
  cardName: {
    color: '#fff',
    fontSize: '14px',
    fontWeight: 600,
    flex: 1,
  },
  cardId: {
    color: '#666',
    fontSize: '11px',
    fontFamily: 'monospace',
  },
  cardContent: {
    padding: '12px',
    minHeight: '80px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  cardFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '8px 12px',
    borderTop: '1px solid rgba(255,255,255,0.1)',
    background: 'rgba(0,0,0,0.2)',
  },
  propCount: {
    color: '#666',
    fontSize: '12px',
  },
  expandButton: {
    background: 'transparent',
    border: '1px solid rgba(255,255,255,0.2)',
    borderRadius: '4px',
    padding: '4px 8px',
    color: '#888',
    fontSize: '11px',
    cursor: 'pointer',
  },
  propsPanel: {
    borderTop: '1px solid rgba(255,255,255,0.1)',
    background: 'rgba(0,0,0,0.3)',
    maxHeight: '200px',
    overflow: 'auto',
  },
  propsCode: {
    margin: 0,
    padding: '12px',
    fontSize: '11px',
    fontFamily: 'monospace',
    color: '#aaa',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
};

export default ComponentGallery;
