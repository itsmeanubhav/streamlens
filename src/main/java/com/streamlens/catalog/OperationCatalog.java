package com.streamlens.catalog;

import java.util.Map;

import static com.streamlens.catalog.OperationCategory.INTERMEDIATE;
import static com.streamlens.catalog.OperationCategory.TERMINAL;

/**
 * Lookup table of Java 8 Stream API operations StreamLens understands in the MVP.
 * This is what lets the backend say "filter is intermediate, here's generally what it does"
 * without calling any AI - it's deterministic, reviewed-by-a-human text.
 *
 * MVP covers the operations most beginners actually hit. Easy to extend later
 * (mapToInt, boxed, takeWhile, dropWhile, etc.) by adding one entry.
 */
public final class OperationCatalog {

    private static final Map<String, OperationInfo> OPS = Map.ofEntries(
            // --- Intermediate operations (lazy: queued, don't run until a terminal op fires) ---
            Map.entry("filter", new OperationInfo("filter", INTERMEDIATE, true,
                    "Goes through the items one by one and asks a yes/no question about each. " +
                    "Only the ones that say \"yes\" move on; the rest get dropped. " +
                    "(It's \"lazy\" - this doesn't actually run until a later step asks for the result.)")),

            Map.entry("map", new OperationInfo("map", INTERMEDIATE, true,
                    "Takes every item and turns it into something else - same number of items in, " +
                    "same number out, just changed. Also lazy, like filter.")),

            Map.entry("flatMap", new OperationInfo("flatMap", INTERMEDIATE, true,
                    "For when each item is itself a small list. This opens up every item's inner list " +
                    "and mixes them all into one single flat list. Also lazy.")),

            Map.entry("sorted", new OperationInfo("sorted", INTERMEDIATE, true,
                    "Puts the items in order - same items, same count, just rearranged. Also lazy.")),

            Map.entry("distinct", new OperationInfo("distinct", INTERMEDIATE, true,
                    "Removes repeats, keeping only the first copy of each item. Also lazy.")),

            Map.entry("limit", new OperationInfo("limit", INTERMEDIATE, true,
                    "Keeps only the first few items and throws the rest away. Also lazy.")),

            Map.entry("skip", new OperationInfo("skip", INTERMEDIATE, true,
                    "Throws away the first few items and keeps the rest. Also lazy.")),

            Map.entry("peek", new OperationInfo("peek", INTERMEDIATE, true,
                    "Lets you glance at each item as it passes by, usually just to print it for " +
                    "debugging. Doesn't change the data at all. Also lazy.")),

            // --- Terminal operations (trigger execution, produce a final result) ---
            Map.entry("collect", new OperationInfo("collect", TERMINAL, false,
                    "This is a terminal operation: it triggers the stream pipeline and gathers the processed " +
                    "elements into a result container such as a List or Set.")),

            Map.entry("forEach", new OperationInfo("forEach", TERMINAL, false,
                    "This makes everything above it run, then does something with each final item - " +
                    "most often just printing it.")),

            Map.entry("reduce", new OperationInfo("reduce", TERMINAL, false,
                    "This makes everything above it run, then squashes all the items down into one " +
                    "single answer - like adding them all up.")),

            Map.entry("count", new OperationInfo("count", TERMINAL, false,
                    "This makes everything above it run, then tells you how many items came out the " +
                    "other end.")),

            Map.entry("anyMatch", new OperationInfo("anyMatch", TERMINAL, false,
                    "This checks: does at least one item pass the test? True or false.")),

            Map.entry("allMatch", new OperationInfo("allMatch", TERMINAL, false,
                    "This checks: do all the items pass the test? True or false.")),

            Map.entry("noneMatch", new OperationInfo("noneMatch", TERMINAL, false,
                    "This checks: does not even one item pass the test? True or false.")),

            Map.entry("findFirst", new OperationInfo("findFirst", TERMINAL, false,
                    "This grabs just the first item, if there is one.")),

            Map.entry("findAny", new OperationInfo("findAny", TERMINAL, false,
                    "This grabs some item - not necessarily the first one.")),

            Map.entry("min", new OperationInfo("min", TERMINAL, false,
                    "This finds the smallest item.")),

            Map.entry("max", new OperationInfo("max", TERMINAL, false,
                    "This finds the largest item.")),
					
			Map.entry("mapToInt", new OperationInfo("mapToInt", INTERMEDIATE, true,
					"Converts each item into a primitive int value. The result is an IntStream, which supports numeric terminal operations such as sum(), min() and max().")),

			Map.entry("mapToLong", new OperationInfo("mapToLong", INTERMEDIATE, true,
					"Converts each item into a long value, creating a LongStream.")),

			Map.entry("mapToDouble", new OperationInfo("mapToDouble", INTERMEDIATE, true,
					"Converts each item into a double value, creating a DoubleStream.")),

			Map.entry("boxed", new OperationInfo("boxed", INTERMEDIATE, true,
					"Converts primitive values like int back into their wrapper objects, like Integer.")),
			Map.entry("sum", new OperationInfo("sum", TERMINAL, false,
			"Adds up all the int values in the stream and returns the total as a single int.")),

			Map.entry("average", new OperationInfo("average", TERMINAL, false,
			"Calculates the arithmetic mean of all values in the stream. Returns an OptionalDouble."))
    );

    private OperationCatalog() {
    }

    public static OperationInfo lookup(String methodName) {
        return OPS.get(methodName);
    }

    public static boolean isKnownOperation(String methodName) {
        return OPS.containsKey(methodName);
    }

    public static boolean isTerminal(String methodName) {
        OperationInfo info = OPS.get(methodName);
        return info != null && info.category() == TERMINAL;
    }
}
