package app.nn.agent;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, zero-dependency syntax highlighter for code snippets.
 * Uses an One Dark / VS Code Dark theme palette to style programming languages
 * with colored spans.
 */
public final class SyntaxHighlighter {

    // One Dark / VS Code Dark standard color palette
    public static final int COLOR_KEYWORD = 0xFFC678DD;   // Purple / Magenta
    public static final int COLOR_STRING = 0xFF98C379;    // Olive Green
    public static final int COLOR_NUMBER = 0xFFD19A66;    // Amber / Orange
    public static final int COLOR_COMMENT = 0xFF7F848E;   // Muted Slate Gray
    public static final int COLOR_FUNCTION = 0xFF61AFEF;  // Cyan / Sky Blue
    public static final int COLOR_TYPE = 0xFFE5C07B;      // Gold / Soft Yellow
    public static final int COLOR_DEFAULT = 0xFFABB2BF;   // Light Gray

    private SyntaxHighlighter() {}

    /**
     * Attempts to heuristic-detect the language from snippet patterns when untagged.
     */
    public static String detectLanguage(String code) {
        if (code == null || code.trim().isEmpty()) return "code";
        String trimmed = code.trim();

        // JSON check: begins with { or [ and contains "key":
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && trimmed.contains("\":")) {
            return "json";
        }

        // Python check: def, import, elif, print(, docstrings, or # comments with indent
        if (Pattern.compile("(^|\\n)\\s*(def\\s+\\w+|import\\s+\\w+|from\\s+\\w+|elif\\s+|class\\s+\\w+:)", Pattern.MULTILINE).matcher(code).find()) {
            return "python";
        }

        // JavaScript / TypeScript: const, let, function, =>, ===, console.log
        if (Pattern.compile("(^|\\n)\\s*(const\\s+|let\\s+|var\\s+|function\\s+|import\\s+.*from|export\\s+|console\\.)", Pattern.MULTILINE).matcher(code).find()
                || code.contains("=>") || code.contains("===")) {
            return "javascript";
        }

        // Shell / Bash: shebang, sudo, export, echo, apt, npm, git
        if (trimmed.startsWith("#!/") || Pattern.compile("(^|\\n)\\s*(sudo\\s+|export\\s+\\w+=|echo\\s+|grep\\s+|chmod\\s+|apt\\s+|npm\\s+|git\\s+)", Pattern.MULTILINE).matcher(code).find()) {
            return "bash";
        }

        // SQL: SELECT, INSERT, UPDATE, DELETE, FROM, WHERE
        if (Pattern.compile("(^|\\s)(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|CREATE\\s+TABLE)\\s+", Pattern.CASE_INSENSITIVE).matcher(code).find()) {
            return "sql";
        }

        // Java / Kotlin / C++: public class, package, fun, #include, namespace
        if (Pattern.compile("(^|\\n)\\s*(public\\s+class|package\\s+[\\w.]+|fun\\s+\\w+|#include\\s*<|using\\s+namespace)", Pattern.MULTILINE).matcher(code).find()) {
            return "java";
        }

        // HTML / XML: tags like <html>, <div>, <?xml
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<?xml") || Pattern.compile("<[a-zA-Z0-9_-]+(\\s+[^>]+)?>.*</[a-zA-Z0-9_-]+>", Pattern.DOTALL).matcher(code).find()) {
            return "xml";
        }

        return "code";
    }

    /**
     * Highlights the code content and returns a SpannableStringBuilder with styled tokens.
     */
    public static CharSequence highlight(String code, String lang) {
        if (code == null) return "";
        if (code.isEmpty()) return code;

        String normLang = (lang != null && !lang.trim().isEmpty())
                ? lang.trim().toLowerCase(Locale.ROOT)
                : detectLanguage(code);

        SpannableStringBuilder sb = new SpannableStringBuilder(code);

        switch (normLang) {
            case "python":
            case "py":
                highlightPython(sb, code);
                break;
            case "javascript":
            case "js":
            case "typescript":
            case "ts":
            case "jsx":
            case "tsx":
                highlightJavaScript(sb, code);
                break;
            case "bash":
            case "sh":
            case "shell":
            case "zsh":
                highlightBash(sb, code);
                break;
            case "json":
                highlightJson(sb, code);
                break;
            case "sql":
                highlightSql(sb, code);
                break;
            case "java":
            case "kt":
            case "kotlin":
            case "c":
            case "cpp":
            case "c++":
            case "cs":
            case "csharp":
                highlightCStyle(sb, code);
                break;
            case "xml":
            case "html":
                highlightXml(sb, code);
                break;
            default:
                highlightGeneric(sb, code);
                break;
        }

        return sb;
    }

    // ------------------------------------------------------------- Language Lexers

    private static void highlightPython(SpannableStringBuilder sb, String code) {
        // Comments: # ...
        applyPattern(sb, code, "(#.*)", COLOR_COMMENT, true);

        // Multi-line and single-line strings: """...""", '''...''', "...", '...'
        applyPattern(sb, code, "(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')", COLOR_STRING, false);

        // Decorators: @something
        applyPattern(sb, code, "(@[a-zA-Z_]\\w*)", COLOR_FUNCTION, false);

        // Python Keywords
        String kw = "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|" +
                "except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|" +
                "raise|return|try|while|with|yield)\\b";
        applyPattern(sb, code, kw, COLOR_KEYWORD, false);

        // Constants & Booleans
        applyPattern(sb, code, "\\b(True|False|None|self)\\b", COLOR_NUMBER, false);

        // Built-in types & Exceptions
        applyPattern(sb, code, "\\b(int|str|float|bool|list|dict|set|tuple|Exception|ValueError|TypeError|print|len|range|enumerate|open)\\b", COLOR_TYPE, false);

        // Function definitions & calls: def name(...)
        applyPattern(sb, code, "\\bdef\\s+([a-zA-Z_]\\w*)", 1, COLOR_FUNCTION, false);

        // Numbers: hex, float, integer
        applyPattern(sb, code, "\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*([eE][+-]?\\d+)?)\\b", COLOR_NUMBER, false);
    }

    private static void highlightJavaScript(SpannableStringBuilder sb, String code) {
        // Single and multiline comments: // ... or /* ... */
        applyPattern(sb, code, "(//.*|/\\*[\\s\\S]*?\\*/)", COLOR_COMMENT, true);

        // Strings: "...", '...', `...`
        applyPattern(sb, code, "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|`[\\s\\S]*?`)", COLOR_STRING, false);

        // JS / TS Keywords
        String kw = "\\b(async|await|break|case|catch|class|const|continue|debugger|default|delete|" +
                "do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|of|" +
                "return|super|switch|this|throw|try|typeof|var|void|while|with|yield|type|interface|" +
                "implements|from|as)\\b";
        applyPattern(sb, code, kw, COLOR_KEYWORD, false);

        // Constants & Booleans
        applyPattern(sb, code, "\\b(true|false|null|undefined|NaN|Infinity)\\b", COLOR_NUMBER, false);

        // Types & Objects
        applyPattern(sb, code, "\\b(String|Number|Boolean|Array|Object|Promise|Map|Set|JSON|Math|console|window|document)\\b", COLOR_TYPE, false);

        // Function definitions: function name(
        applyPattern(sb, code, "\\bfunction\\s+([a-zA-Z_$]\\w*)", 1, COLOR_FUNCTION, false);

        // Function calls: name(...)
        applyPattern(sb, code, "\\b([a-zA-Z_$]\\w*)\\s*\\(", 1, COLOR_FUNCTION, false);

        // Numbers
        applyPattern(sb, code, "\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*([eE][+-]?\\d+)?)\\b", COLOR_NUMBER, false);
    }

    private static void highlightBash(SpannableStringBuilder sb, String code) {
        // Comments
        applyPattern(sb, code, "(#.*)", COLOR_COMMENT, true);

        // Strings
        applyPattern(sb, code, "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')", COLOR_STRING, false);

        // Bash Keywords & control flow
        String kw = "\\b(if|then|else|elif|fi|for|in|do|done|while|until|case|esac|select|function|" +
                "return|exit|source|alias|unalias|local|readonly|export|set|unset|trap)\\b";
        applyPattern(sb, code, kw, COLOR_KEYWORD, false);

        // Common commands / utilities
        String cmds = "\\b(sudo|echo|cat|grep|sed|awk|ls|cd|pwd|mkdir|rm|cp|mv|chmod|chown|" +
                "curl|wget|tar|zip|unzip|find|git|ssh|scp|systemctl|service|kill|ps|apt|dnf|pacman|npm|pnpm|yarn)\\b";
        applyPattern(sb, code, cmds, COLOR_TYPE, false);

        // Variables: $VAR, ${VAR}, $1, $?
        applyPattern(sb, code, "(\\$\\w+|\\$\\{[^}]+\\}|\\$[0-9?*@#$!])", COLOR_FUNCTION, false);

        // Numbers
        applyPattern(sb, code, "\\b(\\d+)\\b", COLOR_NUMBER, false);
    }

    private static void highlightJson(SpannableStringBuilder sb, String code) {
        // Key: "key":
        applyPattern(sb, code, "(\"[^\"]+\")\\s*:", 1, COLOR_FUNCTION, false);

        // String value: : "value"
        applyPattern(sb, code, ":\\s*(\"[^\"]*\")", 1, COLOR_STRING, false);

        // Primitive values: numbers, booleans, null
        applyPattern(sb, code, "\\b(true|false|null)\\b", COLOR_KEYWORD, false);
        applyPattern(sb, code, "(:\\s*|-?\\d+\\.?\\d*([eE][+-]?\\d+)?)", COLOR_NUMBER, false);
    }

    private static void highlightSql(SpannableStringBuilder sb, String code) {
        // Comments: -- ... or /* ... */
        applyPattern(sb, code, "(--.*|/\\*[\\s\\S]*?\\*/)", COLOR_COMMENT, true);

        // Strings: '...'
        applyPattern(sb, code, "('[^'\\r\\n]*')", COLOR_STRING, false);

        // SQL Keywords
        String kw = "\\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|DROP|ALTER|" +
                "TABLE|VIEW|INDEX|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|AND|OR|NOT|IN|IS|NULL|" +
                "LIKE|BETWEEN|GROUP\\s+BY|ORDER\\s+BY|HAVING|LIMIT|OFFSET|UNION|ALL|DISTINCT|AS|" +
                "PRIMARY\\s+KEY|FOREIGN\\s+KEY|REFERENCES|CASCADE|DEFAULT|CHECK|UNIQUE)\\b";
        applyPattern(sb, code, kw, COLOR_KEYWORD, false, Pattern.CASE_INSENSITIVE);

        // SQL Types & Functions
        String types = "\\b(INT|INTEGER|BIGINT|VARCHAR|TEXT|CHAR|BOOLEAN|DATE|TIMESTAMP|FLOAT|DOUBLE|" +
                "DECIMAL|COUNT|SUM|AVG|MIN|MAX|NOW|COALESCE)\\b";
        applyPattern(sb, code, types, COLOR_TYPE, false, Pattern.CASE_INSENSITIVE);

        // Numbers
        applyPattern(sb, code, "\\b(\\d+\\.?\\d*)\\b", COLOR_NUMBER, false);
    }

    private static void highlightCStyle(SpannableStringBuilder sb, String code) {
        // Comments
        applyPattern(sb, code, "(//.*|/\\*[\\s\\S]*?\\*/)", COLOR_COMMENT, true);

        // Strings & chars
        applyPattern(sb, code, "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')", COLOR_STRING, false);

        // Preprocessor / annotations
        applyPattern(sb, code, "(#[a-zA-Z_]\\w*|@[a-zA-Z_]\\w*)", COLOR_FUNCTION, false);

        // Keywords
        String kw = "\\b(public|private|protected|static|final|abstract|class|interface|enum|" +
                "extends|implements|package|import|new|return|if|else|switch|case|default|" +
                "for|while|do|break|continue|try|catch|finally|throw|throws|synchronized|" +
                "volatile|transient|native|fun|val|var|override|inline|sealed|data|namespace|" +
                "using|struct|typedef|virtual|const|template|typename)\\b";
        applyPattern(sb, code, kw, COLOR_KEYWORD, false);

        // Types
        String types = "\\b(void|int|long|short|byte|float|double|char|boolean|bool|String|Integer|" +
                "Long|Float|Double|Boolean|List|Map|Set|ArrayList|HashMap|Object)\\b";
        applyPattern(sb, code, types, COLOR_TYPE, false);

        // Constants
        applyPattern(sb, code, "\\b(true|false|null|nullptr|this|super)\\b", COLOR_NUMBER, false);

        // Numbers
        applyPattern(sb, code, "\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*[fFdDlL]?)\\b", COLOR_NUMBER, false);
    }

    private static void highlightXml(SpannableStringBuilder sb, String code) {
        // Comments: <!-- ... -->
        applyPattern(sb, code, "(<!--[\\s\\S]*?-->)", COLOR_COMMENT, true);

        // Tag names: <tag or </tag>
        applyPattern(sb, code, "</?([a-zA-Z0-9_:-]+)", 1, COLOR_KEYWORD, false);

        // Attribute names: name="
        applyPattern(sb, code, "([a-zA-Z0-9_:-]+)=", 1, COLOR_TYPE, false);

        // Attribute values: "..."
        applyPattern(sb, code, "(=?\\s*\"[^\"]*\")", COLOR_STRING, false);
    }

    private static void highlightGeneric(SpannableStringBuilder sb, String code) {
        // Comments: # or //
        applyPattern(sb, code, "(#.*|//.*|/\\*[\\s\\S]*?\\*/)", COLOR_COMMENT, true);

        // Strings
        applyPattern(sb, code, "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*')", COLOR_STRING, false);

        // Common keywords
        String kw = "\\b(if|else|for|while|return|function|def|class|import|const|let|var|true|false|null)\\b";
        applyPattern(sb, code, kw, COLOR_KEYWORD, false);

        // Numbers
        applyPattern(sb, code, "\\b(\\d+\\.?\\d*)\\b", COLOR_NUMBER, false);
    }

    // ------------------------------------------------------------- Span Helpers

    private static void applyPattern(SpannableStringBuilder sb, String text, String regex, int color, boolean italic) {
        applyPattern(sb, text, regex, 0, color, italic, 0);
    }

    private static void applyPattern(SpannableStringBuilder sb, String text, String regex, int group, int color, boolean italic) {
        applyPattern(sb, text, regex, group, color, italic, 0);
    }

    private static void applyPattern(SpannableStringBuilder sb, String text, String regex, int color, boolean italic, int flags) {
        applyPattern(sb, text, regex, 0, color, italic, flags);
    }

    private static void applyPattern(SpannableStringBuilder sb, String text, String regex, int group, int color, boolean italic, int flags) {
        try {
            Matcher m = Pattern.compile(regex, flags).matcher(text);
            while (m.find()) {
                int start = m.start(group);
                int end = m.end(group);
                if (start >= 0 && end <= sb.length() && start < end) {
                    sb.setSpan(new ForegroundColorSpan(color), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (italic) {
                        sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
