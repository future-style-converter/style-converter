/**
 * Performance Profiler for SDUI Rendering.
 *
 * Tracks and reports performance metrics for component rendering.
 */

interface RenderMetrics {
  componentId: string;
  componentName: string;
  renderTime: number;
  propertyCount: number;
  childCount: number;
  timestamp: number;
}

interface ProfilerStats {
  totalRenders: number;
  averageRenderTime: number;
  maxRenderTime: number;
  slowestComponent: string | null;
  metricsHistory: RenderMetrics[];
}

class PerformanceProfilerClass {
  private metrics: RenderMetrics[] = [];
  private enabled = false;
  private maxHistorySize = 1000;

  /**
   * Enable profiling.
   */
  enable() {
    this.enabled = true;
    console.log('[PerformanceProfiler] Enabled');
  }

  /**
   * Disable profiling.
   */
  disable() {
    this.enabled = false;
    console.log('[PerformanceProfiler] Disabled');
  }

  /**
   * Check if profiling is enabled.
   */
  isEnabled(): boolean {
    return this.enabled;
  }

  /**
   * Record a render metric.
   */
  recordRender(
    componentId: string,
    componentName: string,
    renderTime: number,
    propertyCount: number,
    childCount: number
  ) {
    if (!this.enabled) return;

    const metric: RenderMetrics = {
      componentId,
      componentName,
      renderTime,
      propertyCount,
      childCount,
      timestamp: Date.now(),
    };

    this.metrics.push(metric);

    // Trim history if too large
    if (this.metrics.length > this.maxHistorySize) {
      this.metrics = this.metrics.slice(-this.maxHistorySize);
    }

    // Log slow renders
    if (renderTime > 16) {
      // > 1 frame at 60fps
      console.warn(
        `[PerformanceProfiler] Slow render: ${componentName} (${renderTime.toFixed(2)}ms)`
      );
    }
  }

  /**
   * Wrap a render function with timing.
   */
  timeRender<T>(
    componentId: string,
    componentName: string,
    propertyCount: number,
    childCount: number,
    renderFn: () => T
  ): T {
    if (!this.enabled) {
      return renderFn();
    }

    const startTime = performance.now();
    const result = renderFn();
    const endTime = performance.now();

    this.recordRender(
      componentId,
      componentName,
      endTime - startTime,
      propertyCount,
      childCount
    );

    return result;
  }

  /**
   * Get profiler statistics.
   */
  getStats(): ProfilerStats {
    if (this.metrics.length === 0) {
      return {
        totalRenders: 0,
        averageRenderTime: 0,
        maxRenderTime: 0,
        slowestComponent: null,
        metricsHistory: [],
      };
    }

    const totalRenderTime = this.metrics.reduce((sum, m) => sum + m.renderTime, 0);
    const maxMetric = this.metrics.reduce((max, m) =>
      m.renderTime > max.renderTime ? m : max
    );

    return {
      totalRenders: this.metrics.length,
      averageRenderTime: totalRenderTime / this.metrics.length,
      maxRenderTime: maxMetric.renderTime,
      slowestComponent: maxMetric.componentName,
      metricsHistory: [...this.metrics],
    };
  }

  /**
   * Get metrics for a specific component.
   */
  getComponentMetrics(componentId: string): RenderMetrics[] {
    return this.metrics.filter((m) => m.componentId === componentId);
  }

  /**
   * Get slow renders (> threshold ms).
   */
  getSlowRenders(thresholdMs = 16): RenderMetrics[] {
    return this.metrics.filter((m) => m.renderTime > thresholdMs);
  }

  /**
   * Clear all metrics.
   */
  clear() {
    this.metrics = [];
    console.log('[PerformanceProfiler] Metrics cleared');
  }

  /**
   * Export metrics as JSON.
   */
  exportMetrics(): string {
    return JSON.stringify(
      {
        exportedAt: new Date().toISOString(),
        stats: this.getStats(),
        metrics: this.metrics,
      },
      null,
      2
    );
  }

  /**
   * Print a summary to console.
   */
  printSummary() {
    const stats = this.getStats();
    console.group('[PerformanceProfiler] Summary');
    console.log(`Total Renders: ${stats.totalRenders}`);
    console.log(`Average Render Time: ${stats.averageRenderTime.toFixed(2)}ms`);
    console.log(`Max Render Time: ${stats.maxRenderTime.toFixed(2)}ms`);
    console.log(`Slowest Component: ${stats.slowestComponent || 'N/A'}`);
    console.log(`Slow Renders (>16ms): ${this.getSlowRenders().length}`);
    console.groupEnd();
  }
}

// Singleton instance
export const PerformanceProfiler = new PerformanceProfilerClass();

export default PerformanceProfiler;
