// IsolationConfig.ts — typed record for the CSS `isolation` property.
// IR emits bare uppercase strings: 'AUTO' | 'ISOLATE'.

// Config holder — optional so absence is distinguishable.
export interface IsolationConfig {
  value?: 'auto' | 'isolate';                                         // lowercased CSS token
}

// IR property type recognised here.
export const ISOLATION_PROPERTY_TYPE = 'Isolation' as const;
export type IsolationPropertyType = typeof ISOLATION_PROPERTY_TYPE;
