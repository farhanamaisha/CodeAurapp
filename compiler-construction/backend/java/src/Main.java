
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
import java.util.ArrayList;
import java.util.List;

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

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", 8080), 0
        );

        server.createContext("/api/compile", Main::handleCompile);
        server.start();

        System.out.println("Java Compiler API running on port 8080");
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

            if (source == null || source.trim().isEmpty()) {
                throw new RuntimeException(
                    "Please enter some Java source code."
                );
            }

            List<TokenInfo> tokens = tokenize(source);

            JavaLexer lexer = new JavaLexer(new StringReader(source));
            JavaParser parser = new JavaParser(lexer);

            Symbol parsed = parser.parse();
            ASTNode ast = (ASTNode) parsed.value;

            if (ast == null) {
                throw new RuntimeException(
                    "Syntax Error: Parser did not generate an AST."
                );
            }

            SemanticAnalyzer semantic = new SemanticAnalyzer();
            List<String> errors = semantic.analyze(ast);

            List<symboltable.Symbol> symbols =
                semantic.getSymbolTable().getAllSymbols();

            List<String> tacInstructions = List.of();
            List<String> programOutput = List.of();
            List<String> runtimeErrors = new ArrayList<>();

            if (errors.isEmpty()) {
                TACGenerator tac = new TACGenerator();
                tacInstructions = tac.generate(ast);

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
                programOutput,
                runtimeErrors
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
                + "\"tokens\":[],\"ast\":null,\"symbols\":[],"
                + "\"tac\":[],\"programOutput\":[],"
                + "\"runtimeErrors\":[]}";

            sendResponse(exchange, 200, response);
        }
    }

    private static String classifyError(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("lexical") || lower.contains("unexpected character")) {
            return "Lexical Error";
        }
        if (lower.contains("syntax") || lower.contains("parse")
                || lower.contains("unexpected token")
                || lower.contains("can't recover")) {
            return "Syntax Error";
        }
        if (lower.contains("semantic") || lower.contains("undeclared")
                || lower.contains("already declared")
                || lower.contains("type mismatch")) {
            return "Semantic Error";
        }
        if (lower.contains("runtime") || lower.contains("division by zero")
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

            if (token.sym >= 0 && token.sym < sym.terminalNames.length) {
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
            List<String> programOutput,
            List<String> runtimeErrors) {

        StringBuilder json = new StringBuilder();

        json.append("{\"success\":")
            .append(errors.isEmpty() && runtimeErrors.isEmpty())
            .append(",\"tokens\":[");

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
                ? "null" : jsonString(node.getValue()))
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
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;

                default:
                    if (c < 32) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }

        return result.append("\"").toString();
    }

    private static String extractJsonString(String body, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = body.indexOf(key);

        if (keyIndex < 0) {
            return field.equals("input") ? "" : null;
        }

        int index = body.indexOf(':', keyIndex + key.length()) + 1;

        while (index < body.length()
                && Character.isWhitespace(body.charAt(index))) {
            index++;
        }

        if (index >= body.length() || body.charAt(index) != '"') {
            throw new RuntimeException(
                "Invalid request: " + field + " must be a string."
            );
        }

        index++;
        StringBuilder value = new StringBuilder();

        while (index < body.length()) {
            char c = body.charAt(index++);

            if (c == '"') return value.toString();

            if (c == '\\') {
                if (index >= body.length()) break;

                char escaped = body.charAt(index++);

                switch (escaped) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case '/': value.append('/'); break;

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
            HttpExchange exchange, int status, String response)
            throws IOException {

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
