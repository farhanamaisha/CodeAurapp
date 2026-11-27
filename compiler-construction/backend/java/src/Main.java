
import codegen.BytecodeGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lexer.JavaLexer;
import parser.JavaParser;
import parser.sym;
import java_cup.runtime.Symbol;

import ast.ASTNode;
import semantic.SemanticAnalyzer;
import intermediate.TACGenerator;
import runtime.ProgramExecutor;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Main {

    private static class TokenInfo {
        String type;
        String lexeme;
        int line;

        TokenInfo(String type, String lexeme, int line) {
            this.type = type;
            this.lexeme = lexeme;
            this.line = line;
        }
    }

    private static class ProcessResult {
        int exitCode;
        String stdout;
        String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String portValue = System.getenv("PORT");
        if (portValue != null && !portValue.isBlank()) {
            port = Integer.parseInt(portValue);
        }

        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", port), 0
        );

        server.createContext("/api/health", Main::handleHealth);
        server.createContext("/api/compile", Main::handleCompile);
        server.start();

        System.out.println("Java Compiler API running on port " + port);
    }

    private static void handleHealth(HttpExchange exchange)
            throws IOException {
        addCorsHeaders(exchange);
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendResponse(exchange, 405,
                "{\"success\":false,\"errors\":[\"Method not allowed\"]}");
            return;
        }
        sendResponse(exchange, 200,
            "{\"success\":true,\"service\":\"compiler-api\"}");
    }

    private static void handleCompile(HttpExchange exchange)
            throws IOException {

        addCorsHeaders(exchange);

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405,
                "{\"success\":false,\"errors\":[\"Method not allowed\"]}");
            return;
        }

        try {
            String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
            );

            String source = extractJsonString(body, "source");
            String input = extractJsonString(body, "input");
            String language = extractJsonString(body, "language");
            if (language == null) language = "Java";

            if (source == null || source.trim().isEmpty()) {
                throw new RuntimeException(
                    "Please enter " + language + " source code."
                );
            }

            if (!language.equalsIgnoreCase("Java")) {
                String externalResponse =
                    compileExternalLanguage(language, source, input);
                sendResponse(exchange, 200, externalResponse);
                return;
            }

            // Phase 1: Lexical Analysis
            List<TokenInfo> tokens = tokenize(source);


            // Phase 2: Syntax Analysis
            JavaLexer lexer = new JavaLexer(new StringReader(source));
            JavaParser parser = new JavaParser(lexer);

            Symbol parsed = parser.parse();
            ASTNode ast = (ASTNode) parsed.value;

            if (ast == null) {
                throw new RuntimeException(
                    "Syntax Error: Parser did not generate an AST."
                );
            }

            // Phase 3: Semantic Analysis
            SemanticAnalyzer semantic = new SemanticAnalyzer();
            List<String> errors = semantic.analyze(ast);

            List<symboltable.Symbol> symbols =
                semantic.getSymbolTable().getAllSymbols();

            List<String> tacInstructions = new ArrayList<>();
            List<String> optimizedTac = new ArrayList<>();
            List<String> programOutput = new ArrayList<>();
            List<String> runtimeErrors = new ArrayList<>();

            String generatedBytecode = "";
            String generatedClassFile = "";
            String codeGenerationError = "";

            boolean intermediateCompleted = false;
            boolean optimizationCompleted = false;
            boolean codeGenerationCompleted = false;

            if (errors.isEmpty()) {

                TACGenerator tac = new TACGenerator();

                // Phase 4: Intermediate Code Generation
                tacInstructions = tac.generate(ast);
                intermediateCompleted = true;

                // Phase 5: Code Optimization
                optimizedTac = tac.optimize(ast);
                optimizationCompleted = true;

                // Phase 6: JVM Bytecode Generation
                try {
                    BytecodeGenerator generator =
                        new BytecodeGenerator();

                    BytecodeGenerator.Result result =
                        generator.generate(ast.getValue(), optimizedTac);

                    generatedBytecode = result.bytecode;
                    generatedClassFile = result.filePath;
                    codeGenerationCompleted = true;

                } catch (Exception e) {
                    codeGenerationError = e.getMessage() == null
                        ? "JVM bytecode generation failed."
                        : e.getMessage();
                }

                // Execute original AST to preserve Scanner support.
                try {
                    ProgramExecutor executor = new ProgramExecutor();
                    programOutput = executor.execute(ast, input);

                } catch (RuntimeException e) {
                    runtimeErrors.add(
                        e.getMessage() == null
                            ? "Runtime Error: Program execution failed."
                            : e.getMessage()
                    );
                }
            }

            String response = buildResponse(
                tokens,
                ast,
                symbols,
                errors,
                tacInstructions,
                optimizedTac,
                programOutput,
                runtimeErrors,
                intermediateCompleted,
                optimizationCompleted,
                generatedBytecode,
                generatedClassFile,
                codeGenerationCompleted,
                codeGenerationError
            );

            sendResponse(exchange, 200, response);

        } catch (Exception e) {

            String message = e.getMessage() == null
                ? "Internal Compiler Error: Unknown error."
                : e.getMessage();

            String category = classifyError(message);

            String response =
                "{\"success\":false,"
                + "\"errorType\":" + jsonString(category) + ","
                + "\"errors\":[" + jsonString(message) + "],"
                + "\"className\":\"\","
                + "\"tokens\":[],\"ast\":null,\"symbols\":[],"
                + "\"tac\":[],\"optimizedTac\":[],"
                + "\"programOutput\":[],\"runtimeErrors\":[],"
                + "\"intermediateCompleted\":false,"
                + "\"optimizationCompleted\":false,"
                + "\"bytecode\":\"\","
                + "\"generatedClassFile\":\"\","
                + "\"codeGenerationCompleted\":false,"
                + "\"codeGenerationError\":\"\"}";

            sendResponse(exchange, 200, response);
        }
    }

    private static String compileExternalLanguage(
            String language, String source, String input) throws IOException {

        Path root = findRepositoryRoot();
        Path tempSource = Files.createTempFile(
            "compiler-" + language.toLowerCase(Locale.ROOT),
            language.equalsIgnoreCase("C++") ? ".cpp" : ".c"
        );
        Files.writeString(tempSource, source, StandardCharsets.UTF_8);

        try {
            if (language.equalsIgnoreCase("C")) {
                Path cDirectory = root.resolve("compiler-construction/backend/c");
                runProcess(List.of("make", "-C", cDirectory.toString()),
                           "", cDirectory);
                ProcessResult result = runProcess(
                    List.of(cDirectory.resolve("c-compiler").toString(),
                             "--run", tempSource.toString()),
                    input,
                    cDirectory
                );
                return buildCResponse(result);
            }

            if (language.equalsIgnoreCase("C++")) {
                Path cppDirectory =
                    root.resolve("compiler-construction/backend/cpp");
                Path binary = cppDirectory.resolve("build/cpp_compiler");
                if (!Files.isExecutable(binary)) {
                    runProcess(List.of(
                        "cmake", "-S", cppDirectory.toString(),
                        "-B", cppDirectory.resolve("build").toString()
                    ), "", cppDirectory);
                    runProcess(List.of(
                        "cmake", "--build",
                        cppDirectory.resolve("build").toString()
                    ), "", cppDirectory);
                }
                ProcessResult result = runProcess(
                    List.of(binary.toString(), tempSource.toString()),
                    input,
                    cppDirectory
                );
                if (result.exitCode == 0 ||
                    result.stdout.trim().startsWith("{")) {
                    return result.stdout;
                }
                return "{\"success\":false,\"errors\":["
                    + jsonString(result.stderr.isBlank()
                        ? "C++ compilation failed." : result.stderr.trim())
                    + "],\"tokens\":[],\"ast\":null,\"symbols\":[],"
                    + "\"tac\":[],\"optimized_tac\":[],\"code\":[],"
                    + "\"programOutput\":[],\"runtimeErrors\":[]}";
            }
        } finally {
            Files.deleteIfExists(tempSource);
        }

        return "{\"success\":false,\"errors\":["
            + jsonString("Unsupported language: " + language)
            + "],\"tokens\":[],\"ast\":null,\"symbols\":[],"
            + "\"tac\":[],\"programOutput\":[],\"runtimeErrors\":[]}";
    }

    private static String buildCResponse(ProcessResult result) {
        String stdout = result.stdout;
        boolean successful = result.exitCode == 0
            && stdout.contains("Status: compiled and executed successfully");

        String tokenSection = extractSection(
            stdout, "=== 1. LEXICAL ANALYSIS: TOKENS ===",
            "\n=== 2.");
        String astSection = extractSection(
            stdout, "=== 2. SYNTAX ANALYSIS: AST ===",
            "\n=== 3.");
        String symbolSection = extractSection(
            stdout, "=== SYMBOL TABLE ===",
            "\nSemantic Checks:");
        String annotatedAstSection = extractSection(
            stdout, "=== 3.1 ANNOTATED AST ===",
            "\n=== 4.");
        String tacSection = extractSection(
            stdout, "=== 4. INTERMEDIATE CODE GENERATION: TAC ===",
            "\n=== 5.");
        String optimizedSection = extractSection(
            stdout, "=== 5. CODE OPTIMIZATION: OPTIMIZED TAC ===",
            "\n=== 6.");
        String assemblySection = extractSection(
            stdout, "Assembly Code:", "\nExecution:");
        String diagnosticSection = extractSection(
            stdout, "=== DIAGNOSTICS ===", "\n=== 6.");

        List<String> errors = new ArrayList<>();
        if (!diagnosticSection.isEmpty()) {
            for (String line : diagnosticSection.split("\\R")) {
                if (!line.trim().isEmpty()) errors.add(line.trim());
            }
        } else if (result.stderr != null && !result.stderr.trim().isEmpty()
                   && result.exitCode != 0) {
            errors.add(result.stderr.trim());
        }

        StringBuilder output = new StringBuilder();
        output.append("{\"success\":").append(successful).append(",");
        output.append("\"tokens\":").append(parseCTokens(tokenSection)).append(",");
        output.append("\"ast\":").append(astSection.isEmpty()
            ? "null" : jsonString(astSection.trim())).append(",");
        output.append("\"symbols\":").append(parseCSymbols(symbolSection)).append(",");
        output.append("\"annotatedAst\":").append(annotatedAstSection.isEmpty()
            ? "null" : jsonString(annotatedAstSection.trim())).append(",");
        output.append("\"tac\":").append(parseCTac(tacSection)).append(",");
        output.append("\"optimized_tac\":").append(
            parseCTac(optimizedSection)).append(",");
        output.append("\"code\":").append(parseAssemblyCode(
            assemblySection)).append(",");
        output.append("\"errors\":").append(stringListJson(errors)).append(",");
        output.append("\"programOutput\":").append(parseCProgramOutput(stdout)).append(",");
        output.append("\"runtimeErrors\":[]}");
        return output.toString();
    }

    private static String extractSection(
            String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = text.indexOf(endMarker, start);
        if (end < 0) end = text.length();
        return text.substring(start, end).trim();
    }

    private static String parseCTokens(String section) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        if (!section.isEmpty()) {
            for (String line : section.split("\\R")) {
                String[] columns = line.split("\\t", -1);
                if (columns.length < 3) continue;
                if (!first) json.append(",");
                first = false;
                json.append("{\"type\":").append(jsonString(columns[0].trim()))
                    .append(",\"lexeme\":").append(jsonString(columns[1].trim()))
                    .append(",\"line\":");
                try {
                    json.append(Integer.parseInt(
                        columns[2].replace("line", "").trim()));
                } catch (NumberFormatException e) {
                    json.append(1);
                }
                json.append("}");
            }
        }
        return json.append("]").toString();
    }

    private static String parseCSymbols(String section) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        if (!section.isEmpty()) {
            for (String line : section.split("\\R")) {
                String[] columns = line.trim().split("\\s+");
                if (columns.length < 3) continue;
                if (!first) json.append(",");
                first = false;
                json.append("{\"name\":").append(jsonString(columns[0]))
                    .append(",\"type\":").append(jsonString(columns[1]))
                    .append(",\"line\":");
                try {
                    json.append(Integer.parseInt(columns[2].replace("line", "").trim()));
                } catch (NumberFormatException e) {
                    json.append(1);
                }
                json.append("}");
            }
        }
        return json.append("]").toString();
    }

    private static String parseCTac(String section) {
        List<String> lines = new ArrayList<>();
        if (!section.isEmpty()) {
            for (String line : section.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.contains("->")) {
                    lines.add(trimmed);
                }
            }
        }
        return stringListJson(lines);
    }

    private static String parseAssemblyCode(String section) {
        List<String> lines = new ArrayList<>();
        if (!section.isEmpty()) {
            for (String line : section.split("\\R")) {
                lines.add(line);
            }
        }
        return stringListJson(lines);
    }

    private static String parseCProgramOutput(String stdout) {
        int start = stdout.indexOf("Program output:");
        if (start < 0) return "[]";
        start += "Program output:".length();
        int end = stdout.indexOf("Status:", start);
        if (end < 0) end = stdout.length();
        String text = stdout.substring(start, end).trim();
        List<String> lines = new ArrayList<>();
        if (!text.isEmpty()) {
            for (String line : text.split("\\R")) {
                if (!line.trim().isEmpty()) lines.add(line.trim());
            }
        }
        return stringListJson(lines);
    }

    private static String stringListJson(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) json.append(",");
            json.append(jsonString(values.get(i)));
        }
        return json.append("]").toString();
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("compiler-construction/backend/c"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath();
    }

    private static ProcessResult runProcess(
            List<String> command, String input, Path workingDirectory)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(false);
        Process process = builder.start();
        if (input != null && !input.isEmpty()) {
            try (var stream = process.getOutputStream()) {
                stream.write(input.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
        } else {
            process.getOutputStream().close();
        }
        String stdout;
        String stderr;
        try (var out = process.getInputStream()) {
            stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var err = process.getErrorStream()) {
            stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(1, "", "Process interrupted.");
        }
        return new ProcessResult(exitCode, stdout, stderr);
    }

    private static String classifyError(String message) {

        String lower = message.toLowerCase();

        if (lower.contains("lexical")
                || lower.contains("unexpected character")) {
            return "Lexical Error";
        }

        if (lower.contains("syntax")
                || lower.contains("parse")
                || lower.contains("unexpected token")
                || lower.contains("can't recover")) {
            return "Syntax Error";
        }

        if (lower.contains("semantic")
                || lower.contains("undeclared")
                || lower.contains("not declared")
                || lower.contains("already declared")
                || lower.contains("type mismatch")
                || lower.contains("incompatible")) {
            return "Semantic Error";
        }

        if (lower.contains("runtime")
                || lower.contains("division by zero")
                || lower.contains("no input available")
                || lower.contains("invalid input")) {
            return "Runtime Error";
        }

        return "Compiler Error";
    }

    private static List<TokenInfo> tokenize(String source)
            throws Exception {

        List<TokenInfo> tokens = new ArrayList<>();
        JavaLexer lexer = new JavaLexer(new StringReader(source));

        while (true) {

            Symbol token = lexer.next_token();

            if (token.sym == sym.EOF) break;

            String tokenName = "UNKNOWN";

            if (token.sym >= 0
                    && token.sym < sym.terminalNames.length) {
                tokenName = sym.terminalNames[token.sym];
            }

            String lexeme = token.value == null
                ? ""
                : token.value.toString();

            tokens.add(new TokenInfo(
                tokenName,
                lexeme,
                token.left + 1
            ));
        }

        return tokens;
    }

    private static String buildResponse(
            List<TokenInfo> tokens,
            ASTNode ast,
            List<symboltable.Symbol> symbols,
            List<String> errors,
            List<String> tac,
            List<String> optimizedTac,
            List<String> programOutput,
            List<String> runtimeErrors,
            boolean intermediateCompleted,
            boolean optimizationCompleted,
            String bytecode,
            String generatedClassFile,
            boolean codeGenerationCompleted,
            String codeGenerationError) {

        StringBuilder json = new StringBuilder();

        json.append("{\"success\":")
            .append(errors.isEmpty()
                && runtimeErrors.isEmpty()
                && codeGenerationError.isEmpty());

        json.append(",\"className\":")
            .append(jsonString(ast.getValue()));

        json.append(",\"tokens\":[");

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo token = tokens.get(i);

            if (i > 0) json.append(",");

            json.append("{")
                .append("\"type\":").append(jsonString(token.type))
                .append(",\"lexeme\":").append(jsonString(token.lexeme))
                .append(",\"line\":").append(token.line)
                .append("}");
        }

        json.append("],\"ast\":");
        appendAst(json, ast);

        json.append(",\"symbols\":[");

        for (int i = 0; i < symbols.size(); i++) {
            symboltable.Symbol symbol = symbols.get(i);

            if (i > 0) json.append(",");

            json.append("{")
                .append("\"name\":").append(jsonString(symbol.getName()))
                .append(",\"type\":").append(jsonString(symbol.getType()))
                .append(",\"line\":").append(symbol.getLine())
                .append("}");
        }

        json.append("],\"errors\":");
        appendStringList(json, errors);

        json.append(",\"tac\":");
        appendStringList(json, tac);

        json.append(",\"optimizedTac\":");
        appendStringList(json, optimizedTac);

        json.append(",\"intermediateCompleted\":")
            .append(intermediateCompleted);

        json.append(",\"optimizationCompleted\":")
            .append(optimizationCompleted);

        json.append(",\"bytecode\":")
            .append(jsonString(bytecode));

        json.append(",\"generatedClassFile\":")
            .append(jsonString(generatedClassFile));

        json.append(",\"codeGenerationCompleted\":")
            .append(codeGenerationCompleted);

        json.append(",\"codeGenerationError\":")
            .append(jsonString(codeGenerationError));

        json.append(",\"programOutput\":");
        appendStringList(json, programOutput);

        json.append(",\"runtimeErrors\":");
        appendStringList(json, runtimeErrors);

        json.append("}");

        return json.toString();
    }

    private static void appendAst(StringBuilder json, ASTNode node) {

        if (node == null) {
            json.append("null");
            return;
        }

        json.append("{")
            .append("\"type\":").append(jsonString(node.getType()))
            .append(",\"value\":")
            .append(node.getValue() == null
                ? "null"
                : jsonString(node.getValue()))
            .append(",\"line\":").append(node.getLine())
            .append(",\"children\":[");

        List<ASTNode> children = node.getChildren();

        for (int i = 0; i < children.size(); i++) {
            if (i > 0) json.append(",");
            appendAst(json, children.get(i));
        }

        json.append("]}");
    }

    private static void appendStringList(
            StringBuilder json, List<String> values) {

        json.append("[");

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) json.append(",");
            json.append(jsonString(values.get(i)));
        }

        json.append("]");
    }

    private static String jsonString(String value) {

        if (value == null) return "null";

        StringBuilder result = new StringBuilder("\"");

        for (char c : value.toCharArray()) {

            switch (c) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                default:
                    if (c < 32) {
                        result.append(
                            String.format("\\u%04x", (int) c)
                        );
                    } else {
                        result.append(c);
                    }
            }
        }

        return result.append("\"").toString();
    }

    private static String extractJsonString(
            String body, String field) {

        String key = "\"" + field + "\"";
        int keyIndex = body.indexOf(key);

        if (keyIndex < 0) {
            return field.equals("input") ? "" : null;
        }

        int colonIndex = body.indexOf(
            ':', keyIndex + key.length()
        );

        if (colonIndex < 0) {
            throw new RuntimeException(
                "Invalid request: missing value for " + field + "."
            );
        }

        int index = colonIndex + 1;

        while (index < body.length()
                && Character.isWhitespace(body.charAt(index))) {
            index++;
        }

        if (index >= body.length()
                || body.charAt(index) != '"') {
            throw new RuntimeException(
                "Invalid request: " + field + " must be a string."
            );
        }

        index++;

        StringBuilder value = new StringBuilder();

        while (index < body.length()) {

            char c = body.charAt(index++);

            if (c == '"') {
                return value.toString();
            }

            if (c == '\\') {

                if (index >= body.length()) break;

                char escaped = body.charAt(index++);

                switch (escaped) {
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case '"':
                        value.append('"');
                        break;
                    case '\\':
                        value.append('\\');
                        break;
                    case '/':
                        value.append('/');
                        break;
                    case 'u':
                        if (index + 4 > body.length()) {
                            throw new RuntimeException(
                                "Invalid Unicode escape in request."
                            );
                        }

                        try {
                            value.append((char) Integer.parseInt(
                                body.substring(index, index + 4), 16
                            ));
                        } catch (NumberFormatException e) {
                            throw new RuntimeException(
                                "Invalid Unicode escape in request."
                            );
                        }

                        index += 4;
                        break;
                    default:
                        throw new RuntimeException(
                            "Invalid JSON escape in request."
                        );
                }

            } else {
                value.append(c);
            }
        }

        throw new RuntimeException(
            "Invalid JSON string for field: " + field
        );
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set(
            "Access-Control-Allow-Origin", "*"
        );

        exchange.getResponseHeaders().set(
            "Access-Control-Allow-Methods", "POST, OPTIONS"
        );

        exchange.getResponseHeaders().set(
            "Access-Control-Allow-Headers", "Content-Type"
        );
    }

    private static void sendResponse(
            HttpExchange exchange,
            int status,
            String response) throws IOException {

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
