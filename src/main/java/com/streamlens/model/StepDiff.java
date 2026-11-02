package com.streamlens.model;

import java.util.List;

/**
 * Describes exactly how one operation changed the data, in a shape the frontend
 * can render without re-deriving anything. Fields that don't apply to a given
 * operation are left null/empty rather than guessed.
 */
public record StepDiff(
        List<String> removed,          // elements present before, absent after (e.g. filter)
        List<ElementChange> changed,   // before -> after pairs, same count (e.g. map)
        boolean countUnchanged,
        String note                    // short human note, e.g. why something was removed/kept
) {
}
