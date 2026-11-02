package com.streamlens.catalog;

/**
 * Static, hand-written knowledge about one Stream API operation.
 * generalDescription is reused for every occurrence of this operation across any snippet.
 * The per-snippet "specific" explanation (which lambda/method ref was actually used) is
 * built separately in the analysis service, using the arguments JavaParser extracts.
 */
public record OperationInfo(
        String name,
        OperationCategory category,
        boolean lazy,
        String generalDescription
) {
}
