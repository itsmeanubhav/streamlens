package com.streamlens.model;

/** One element's before/after value, used by map-like operations. */
public record ElementChange(String before, String after) {
}
