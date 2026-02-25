package tr.com.cicerali.util;

/**
 * Utility class for handling class name escaping to avoid Java reserved words,
 * conflicts with java.lang classes, and invalid Java identifier characters
 */
public class ClassNameUtil {

    /**
     * Sanitizes and escapes class names to be valid Java identifiers.
     * - Converts hyphens and other invalid characters to camelCase
     * - Escapes reserved words by appending "Type"
     */
    public static String escapeClassName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        // First, sanitize invalid Java identifier characters
        String sanitized = sanitizeIdentifier(name);

        // Then, check for reserved words
        switch (sanitized) {
            case "Object":
            case "String":
            case "Integer":
            case "Long":
            case "Double":
            case "Float":
            case "Boolean":
            case "Byte":
            case "Short":
            case "Character":
            case "Class":
            case "Package":
            case "Module":
            case "Exception":
            case "Error":
            case "Throwable":
            case "System":
            case "Thread":
            case "Runnable":
            case "Serializable":
            case "Comparable":
            case "Cloneable":
            case "Iterable":
            case "Collection":
            case "List":
            case "Set":
            case "Map":
                return sanitized + "Type";
            default:
                return sanitized;
        }
    }

    /**
     * Sanitizes and escapes field names to be valid Java identifiers.
     * Similar to escapeClassName but doesn't append "Type" for reserved words,
     * instead prefixes with underscore.
     */
    public static String sanitizeFieldName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        String sanitized = sanitizeIdentifier(name);

        // Check for reserved words - prefix with underscore instead of appending "Type"
        switch (sanitized) {
            case "abstract":
            case "assert":
            case "boolean":
            case "break":
            case "byte":
            case "case":
            case "catch":
            case "char":
            case "class":
            case "const":
            case "continue":
            case "default":
            case "do":
            case "double":
            case "else":
            case "enum":
            case "extends":
            case "final":
            case "finally":
            case "float":
            case "for":
            case "goto":
            case "if":
            case "implements":
            case "import":
            case "instanceof":
            case "int":
            case "interface":
            case "long":
            case "native":
            case "new":
            case "package":
            case "private":
            case "protected":
            case "public":
            case "return":
            case "short":
            case "static":
            case "strictfp":
            case "super":
            case "switch":
            case "synchronized":
            case "this":
            case "throw":
            case "throws":
            case "transient":
            case "try":
            case "void":
            case "volatile":
            case "while":
            case "true":
            case "false":
            case "null":
                // Java library classes that might conflict
            case "Object":
            case "String":
            case "Integer":
            case "Long":
            case "Double":
            case "Float":
            case "Boolean":
                return "_" + sanitized;
            default:
                return sanitized;
        }
    }

    /**
     * Sanitizes a string to be a valid Java identifier by converting
     * invalid characters to camelCase format.
     * For example: "Object-List" becomes "ObjectList"
     */
    private static String sanitizeIdentifier(String name) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            // Valid Java identifier characters: letters, digits, underscore
            if (Character.isLetterOrDigit(c) || c == '_') {
                if (capitalizeNext && Character.isLetter(c)) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            } else if (c == '-') {
                capitalizeNext = true;
                result.append('_');
            } else if (c == ' ' || c == '.') {
                // Convert hyphens, spaces, and dots to camelCase by capitalizing next letter
                capitalizeNext = true;
            }
            // Other invalid characters are simply skipped
        }

        String sanitized = result.toString();
        // Ensure the result starts with a letter (not a digit or underscore)
        if (sanitized.isEmpty()) {
            return "Type";
        }
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "T" + sanitized;
        }
        return sanitized;
    }
}
