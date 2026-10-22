import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { SOURCE_IDS, validateSourceConfig } from "../functions/_shared/source-config-validation.ts";
import { prepareSourceConfigRollback } from "../functions/_shared/source-config-rollback.ts";

const defaults = JSON.parse(readFileSync(new URL("../../app/src/main/assets/source_config_defaults.json", import.meta.url)));
const cases = JSON.parse(readFileSync(new URL("./source-config-cases.json", import.meta.url)));
for (const scenario of cases) test(scenario.name, () => {
  const document = structuredClone(defaults);
  const path = (scenario.set ?? scenario.delete)?.split(".");
  if (path) {
    const key = path.pop();
    const object = path.reduce((value, key) => value[key], document);
    if (scenario.delete) delete object[key];
    else object[key] = scenario.value;
  }
  assert.equal(validateSourceConfig(document).valid, scenario.valid,
    JSON.stringify(validateSourceConfig(document)));
});

test("only the current seven sources are publishable", () => {
  assert.deepEqual(SOURCE_IDS.toSorted(), ["baddiehub", "bunkr", "fapello", "kaotic", "onlyhaven", "wikifeet", "wikifeetx"]);
  assert.deepEqual(Object.keys(defaults.sources).toSorted(), SOURCE_IDS.toSorted());
});

test("legacy rollback preserves sources and fields absent from historical documents", () => {
  const prior = structuredClone(defaults);
  prior.sources = Object.fromEntries(["fapello", "bunkr", "wikifeet", "wikifeetx"].map(id => [id, prior.sources[id]]));
  prior.sources.fapello.retryCount = 0;
  const restored = prepareSourceConfigRollback(prior, defaults, defaults.configVersion + 1, "2026-10-22T12:00:00Z");
  assert.equal(restored.sources.fapello.retryCount, 0);
  assert.deepEqual(restored.sources.onlyhaven, defaults.sources.onlyhaven);
  assert.deepEqual(restored.sources.kaotic, defaults.sources.kaotic);
  assert.equal(validateSourceConfig(restored).valid, true);
});

test("rollback filters unsupported historical sources and retains optional route data", () => {
  const prior = structuredClone(defaults);
  delete prior.sources.onlyhaven.routes.creatorCategoryApi;
  delete prior.sources.onlyhaven.categoryValues;
  prior.sources.retired_source = { enabled: true };
  const restored = prepareSourceConfigRollback(prior, defaults, defaults.configVersion + 1);
  assert.deepEqual(Object.keys(restored.sources).toSorted(), SOURCE_IDS.toSorted());
  assert.deepEqual(restored.sources.onlyhaven.categoryValues, defaults.sources.onlyhaven.categoryValues);
  assert.equal(validateSourceConfig(restored).valid, true);
});

test("rollback rejects invalid historical values and incompatible schemas", () => {
  const prior = structuredClone(defaults);
  prior.sources.kaotic.baseUrl = "http://unsafe.invalid/";
  assert.throws(() => prepareSourceConfigRollback(prior, defaults, defaults.configVersion + 1));
  prior.schemaVersion = 2;
  assert.throws(() => prepareSourceConfigRollback(prior, defaults, defaults.configVersion + 1), /schema/);
});
