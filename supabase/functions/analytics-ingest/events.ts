import { validAndroidVersion, validDeviceModel } from "./validation.ts";
const allowedMetrics = new Set(["app_open", "app_version", "section", "source", "creator", "device_model", "device_manufacturer", "android_version"]);
const allowedSections = new Set([
  "home", "collections", "library", "chaos", "categories", "search", "favorites",
  "downloads", "settings", "profile", "creator_gallery"
]);
const allowedSources = new Set([
  "crazyshit", "efukt", "fapzone", "fapello", "bunkr", "wikifeet", "wikifeetx", "kaotic", "baddiehub", "xmegadrive", "onlyhaven"
]);
const hexKey = /^[0-9a-f]{64}$/;
const versionValue = /^[A-Za-z0-9._+\-]{1,40}$/;
const safeLabel = /^[^\u0000-\u001F\u007F]{1,120}$/u;


export interface AnalyticsEvent {
  metric: string; value: string; day_key: string; week_key: string; month_key: string;
}
export function validateEvent(input: unknown): AnalyticsEvent {
    if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("Invalid event.");
    const body = input as Record<string, unknown>;
    const metric = body.metric;
    const value = typeof body.value === "string" ? body.value.trim() : "";
    const dayKey = body.day_key;
    const weekKey = body.week_key;
    const monthKey = body.month_key;

    if (!allowedMetrics.has(metric as string)) throw new Error("Unsupported metric.");
    if (typeof dayKey !== "string" || typeof weekKey !== "string" || typeof monthKey !== "string" || !hexKey.test(dayKey) || !hexKey.test(weekKey) || !hexKey.test(monthKey)) {
      throw new Error("Invalid anonymous key.");
    }
    if (metric === "app_open" && value !== "all") throw new Error("Invalid app metric.");
    if (metric === "app_version" && !versionValue.test(value)) throw new Error("Invalid app version.");
    if (metric === "section" && !allowedSections.has(value)) throw new Error("Invalid section.");
    if (metric === "source" && !allowedSources.has(value)) throw new Error("Invalid source.");
    if (metric === "creator" && !safeLabel.test(value)) throw new Error("Invalid creator label.");
    if ((metric === "device_model" || metric === "device_manufacturer") && !validDeviceModel(body.value)) {
      throw new Error("Invalid device model.");
    }
    if (metric === "android_version" && !validAndroidVersion(body.value)) {
      throw new Error("Invalid Android version.");
    }

    return { metric: metric as string, value, day_key: dayKey as string,
      week_key: weekKey as string, month_key: monthKey as string };
}
