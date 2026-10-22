import { SOURCE_IDS, validateSourceConfig } from "./source-config-validation.ts";

function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function overlay(current: unknown, prior: unknown): unknown {
  if (!object(current) || !object(prior)) return prior;
  const result = { ...current };
  for (const [key, value] of Object.entries(prior)) result[key] = overlay(current[key], value);
  return result;
}

/** Preserve current-only sources/fields when restoring an older schema-1 snapshot. */
export function prepareSourceConfigRollback(prior: unknown, active: unknown, nextVersion: number,
  updatedAt = new Date().toISOString()): Record<string, unknown> {
  if (!object(prior) || !object(active) || !object(prior.sources) || !object(active.sources)) {
    throw new Error("Rollback requires stored configuration documents.");
  }
  if (prior.schemaVersion !== active.schemaVersion) throw new Error("Rollback schema is incompatible.");
  const sources: Record<string, unknown> = {};
  for (const id of SOURCE_IDS) {
    sources[id] = id in prior.sources ? overlay(active.sources[id], prior.sources[id]) : active.sources[id];
  }
  const config = { ...prior, sources, configVersion: nextVersion, updatedAt };
  const validation = validateSourceConfig(config);
  if (!validation.valid) throw new Error(`Stored configuration is invalid: ${validation.reason}`);
  return config;
}
