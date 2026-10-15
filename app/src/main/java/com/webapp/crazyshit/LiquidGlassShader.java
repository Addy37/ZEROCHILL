/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0.
 * See licenses/AndroidLiquidGlass-Apache-2.0.txt.
 * Adapted from AndroidLiquidGlass backdrop/internal/Shaders.kt at 65ab177e.
 * Changes: Android View coordinates, uniform pill radius, combined moving lens,
 * bounded zero-safe normals, one backdrop sample, no dispersion.
 */
package com.webapp.crazyshit;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.RectF;
import androidx.annotation.RequiresApi;

@RequiresApi(33)
final class LiquidGlassShader {
    private static final String SOURCE = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float4 lens;
uniform float reach;
uniform float amount;
uniform float pressure;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * (max(cornerCoord, 0.0) / max(length(max(cornerCoord, 0.0)), 0.001));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
float2 bend(float2 point, float2 halfSize, float radius, float height, float strength) {
    float sd = sdRoundedRect(point, halfSize, radius);
    float edge = clamp(1.0 + min(sd, 0.0) / max(height, 0.001), 0.0, 1.0);
    float d = (1.0 - sqrt(max(0.0, 1.0 - edge * edge))) * strength;
    float2 normal = gradSdRoundedRect(point, halfSize, min(radius * 1.5, min(halfSize.x, halfSize.y)));
    return d * normal;
}
half4 main(float2 coord) {
    float2 local = coord - origin;
    float2 halfSize = size * 0.5;
    float2 shift = bend(local - halfSize, halfSize, halfSize.y, reach, -amount);
    float2 lensSize = max(lens.zw - lens.xy, float2(1.0));
    float2 lp = local - (lens.xy + lens.zw) * 0.5;
    float sd = sdRoundedRect(lp, lensSize * 0.5, lensSize.y * 0.5);
    if (lens.z > lens.x && sd < 0.0) {
        shift += bend(lp, lensSize * 0.5, lensSize.y * 0.5, reach * 0.65, -amount * (0.55 + pressure));
        shift += lp * 0.055;
    }
    return content.eval(coord + shift);
}
        """;
    private final RuntimeShader shader = new RuntimeShader(SOURCE);
    private RenderEffect effect;
    private float oldWidth, oldHeight, oldX, oldY, oldDensity, oldPressure;
    private final RectF oldLens = new RectF();

    RenderEffect update(float width, float height, float x, float y, RectF lens, float density, float pressure) {
        if (effect != null && width == oldWidth && height == oldHeight && x == oldX && y == oldY
                && density == oldDensity && pressure == oldPressure && oldLens.equals(lens)) return effect;
        oldWidth = width; oldHeight = height; oldX = x; oldY = y;
        oldDensity = density; oldPressure = pressure; oldLens.set(lens);
        shader.setFloatUniform("size", width, height);
        shader.setFloatUniform("origin", x, y);
        shader.setFloatUniform("lens", lens.left, lens.top, lens.right, lens.bottom);
        shader.setFloatUniform("reach", Math.min(16f * density, height * .38f));
        shader.setFloatUniform("amount", Math.min(18f * density, height * .38f));
        shader.setFloatUniform("pressure", pressure);
        // RenderEffect captures shader uniforms. Rebuild only as geometry/pressure changes,
        // not as the content behind this stationary surface scrolls.
        effect = RenderEffect.createRuntimeShaderEffect(shader, "content");
        return effect;
    }
}
