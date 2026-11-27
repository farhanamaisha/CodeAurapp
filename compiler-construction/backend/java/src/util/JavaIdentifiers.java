package util;

import java.util.Set;
import javax.lang.model.SourceVersion;

public final class JavaIdentifiers {
    private static final Set<String> RESERVED_CLASS_IDENTIFIERS = Set.of(
        "permits", "record", "sealed", "var", "yield"
    );

    private JavaIdentifiers() {}

    public static boolean isValidClassIdentifier(String identifier) {
        return identifier != null
            && SourceVersion.isIdentifier(identifier)
            && !SourceVersion.isKeyword(identifier)
            && !RESERVED_CLASS_IDENTIFIERS.contains(identifier);
    }
}
