/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JiffleFormulaParser {

    private static final Set<String> JIFFLE_KEYWORDS = Set.of(
            "if",
            "else",
            "for",
            "while",
            "return",
            "var",
            "function",
            "import",
            "boolean",
            "int",
            "float",
            "double",
            "string",
            "images",
            "init",
            "read",
            "write",
            "con",
            "options",
            "breakif",
            "break",
            "foreach",
            "in");

    private static final Set<String> JIFFLE_FUNCTIONS = Set.of(
            "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "exp", "floor", "log", "log10", "max", "min", "mod",
            "pow", "random", "round", "sin", "sqrt", "tan");

    private static final Set<String> JIFFLE_CONSTANTS = Set.of("PI", "E");

    private static final Pattern VARIABLES_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    private static final Pattern INPUT_VARIABLES_PATTERN =
            Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?:\\s*\\[\\s*(\\d+)\\s*])?");

    public static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+\\]");

    public static final Pattern OUTPUTVARS_PATTERN =
            Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\[\\s*(\\d+)]\\s*=\\s*(.*?)(?:;\\s*|$)");
    public static final Pattern OUTPUTVARS_SIMPLE_PATTERN =
            Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*?)(?:;\\s*|$)");

    public static class JiffleOutputResult {
        public String outputVar;
        public Map<Integer, String> expressions = new TreeMap<>();
    }

    /**
     * Extracts output variable assignments from a Jiffle script, handling both indexed and simple output formats.
     *
     * @param script The Jiffle script to parse for output assignments
     * @return A JiffleOutputResult containing the output variable name and its corresponding expressions, or an empty
     *     result if multiple inconsistent output variables are detected
     */
    public static JiffleOutputResult extractOutputAssignments(String script) {
        JiffleOutputResult result = new JiffleOutputResult();

        Matcher indexedMatcher = OUTPUTVARS_PATTERN.matcher(script);
        Matcher simpleMatcher = OUTPUTVARS_SIMPLE_PATTERN.matcher(script);

        boolean indexedFound = false;

        // First, process indexed output variables
        while (indexedMatcher.find()) {
            String var = indexedMatcher.group(1);
            int index = Integer.parseInt(indexedMatcher.group(2));
            String expr = indexedMatcher.group(3).trim().replaceAll(";\\s*$", "");

            if (result.outputVar == null) {
                result.outputVar = var;
            } else if (!result.outputVar.equals(var)) {
                // Inconsistent output variable
                return new JiffleOutputResult(); // invalid
            }

            result.expressions.put(index, expr);
            indexedFound = true;
        }

        // If indexed output variables were found, skip the simple output processing
        if (!indexedFound) {
            while (simpleMatcher.find()) {
                String var = simpleMatcher.group(1);
                String expr = simpleMatcher.group(2).trim().replaceAll(";\\s*$", "");

                if (result.outputVar == null) {
                    result.outputVar = var;
                } else if (!result.outputVar.equals(var)) {
                    return new JiffleOutputResult();
                }

                // Assume band index 0 for simple outputs
                result.expressions.put(0, expr);
            }
        }

        return result;
    }

    /**
     * Extracts unique input coverage bands from a Jiffle expression.
     *
     * @param expression The Jiffle script expression to parse
     * @return A list of input coverage bands referenced in the expression
     */
    public static List<CoverageView.InputCoverageBand> extractInputBandsFromExpression(String expression) {
        List<CoverageView.InputCoverageBand> inputBands = new ArrayList<>();
        Matcher matcher = INPUT_VARIABLES_PATTERN.matcher(expression);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String coverage = matcher.group(1);
            String band = matcher.group(2);
            String key = coverage + (band != null ? "@" + band : "");

            if (!seen.contains(key) && !isJiffleReservedWord(coverage)) {
                inputBands.add(new CoverageView.InputCoverageBand(coverage, band));
                seen.add(key);
            }
        }

        return inputBands;
    }

    /**
     * Extracts unique variable references from a Jiffle script, excluding reserved words.
     *
     * @param script The Jiffle script to parse for variable references
     * @return A set of variable names found in the script, excluding Jiffle reserved keywords
     */
    public static Set<String> extractVariableReferences(String script) {
        Set<String> variables = new LinkedHashSet<>();

        Pattern pattern = VARIABLES_PATTERN;
        Matcher matcher = pattern.matcher(script);

        while (matcher.find()) {
            String token = matcher.group(1);
            if (!isJiffleReservedWord(token)) {
                variables.add(token);
            }
        }

        return variables;
    }

    /**
     * Checks if a given word is a reserved word in the Jiffle scripting language.
     *
     * @param word The word to check against Jiffle reserved keywords, functions, and constants
     * @return {@code true} if the word is a Jiffle reserved word, {@code false} otherwise
     */
    public static boolean isJiffleReservedWord(String word) {
        String lower = word.toLowerCase();
        return JIFFLE_KEYWORDS.contains(lower)
                || JIFFLE_FUNCTIONS.contains(lower)
                || JIFFLE_CONSTANTS.contains(word.toUpperCase());
    }

    /**
     * Converts a CoverageBand into a Jiffle script representation.
     *
     * @param band The CoverageBand to convert to a script string
     * @return A Jiffle script string representing the band's output and definition
     */
    public static String extractScriptFromBand(CoverageView.CoverageBand band) {
        return transformToBandInArray(band.getOutputName()) + "=" + band.getDefinition() + ";";
    }

    /**
     * Transforms a band identifier with an "@" separator into an array notation.
     *
     * @param input The input band identifier string, potentially containing an "@" separator
     * @return The transformed band identifier with array notation, or the original input if no transformation is needed
     */
    public static String transformToBandInArray(String input) {
        if (input == null || !input.contains("@")) {
            return input;
        }

        String[] parts = input.split("@", 2); // Split into two parts
        return parts[0] + "[" + parts[1] + "]";
    }
}
