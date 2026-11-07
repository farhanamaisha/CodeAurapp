
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import ast.ASTNode;
import semantic.SemanticAnalyzer;
import symboltable.Symbol;
import intermediate.TACGenerator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {

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

            String source = extractSource(body);

            if (source.trim().isEmpty()) {
                throw new RuntimeException("Please enter some code.");
            }

            List<Token> tokens = new Lexer(source).tokenize();

            ASTNode ast = new Parser(tokens).parse();

            SemanticAnalyzer semantic = new SemanticAnalyzer();
            List<String> errors = semantic.analyze(ast);

            List<Symbol> symbols =
                semantic.getSymbolTable().getAllSymbols();

            List<String> tacInstructions = List.of();

            if (errors.isEmpty()) {
                TACGenerator tac = new TACGenerator();
                tacInstructions = tac.generate(ast);
            }

            String response = buildResponse(
                tokens, ast, symbols, errors, tacInstructions
            );

            sendResponse(exchange, 200, response);

        } catch (RuntimeException e) {

            String message = e.getMessage() == null
                ? "Unknown compiler error."
                : e.getMessage();

            String response =
                "{\"success\":false,\"errors\":["
                + jsonString(message) + "]}";

            sendResponse(exchange, 200, response);

        } catch (Exception e) {

            sendResponse(exchange, 500,
                "{\"success\":false,\"errors\":[\"Internal server error.\"]}");
        }
    }

    private static String buildResponse(
            List<Token> tokens,
            ASTNode ast,
            List<Symbol> symbols,
            List<String> errors,
            List<String> tac) {

        StringBuilder json = new StringBuilder();

        json.append("{\"success\":")
            .append(errors.isEmpty())
            .append(",\"tokens\":[");

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (i > 0) json.append(",");

            json.append("{")
                .append("\"type\":").append(jsonString(token.getType().toString()))
                .append(",\"lexeme\":").append(jsonString(token.getLexeme()))
                .append(",\"line\":").append(token.getLine())
                .append("}");
        }

        json.append("],\"ast\":");
        appendAst(json, ast);

        json.append(",\"symbols\":[");

        for (int i = 0; i < symbols.size(); i++) {
            Symbol symbol = symbols.get(i);

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

        json.append("}");

        return json.toString();
    }

    private static void appendAst(StringBuilder json, ASTNode node) {

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

    private static String extractSource(String body) {

        String key = "\"source\"";
        int keyIndex = body.indexOf(key);

        if (keyIndex < 0) {
            throw new RuntimeException("Missing source field.");
        }

        int index = body.indexOf(':', keyIndex + key.length()) + 1;

        while (index < body.length() &&
                Character.isWhitespace(body.charAt(index))) {
            index++;
        }

        if (index >= body.length() || body.charAt(index) != '"') {
            throw new RuntimeException("Source must be a string.");
        }

        index++;

        StringBuilder source = new StringBuilder();

        while (index < body.length()) {

            char c = body.charAt(index++);

            if (c == '"') {
                return source.toString();
            }

            if (c == '\\') {

                if (index >= body.length()) break;

                char escaped = body.charAt(index++);

                switch (escaped) {
                    case 'n': source.append('\n'); break;
                    case 'r': source.append('\r'); break;
                    case 't': source.append('\t'); break;
                    case 'b': source.append('\b'); break;
                    case 'f': source.append('\f'); break;
                    case '"': source.append('"'); break;
                    case '\\': source.append('\\'); break;
                    case '/': source.append('/'); break;

                    case 'u':
                        if (index + 4 > body.length()) {
                            throw new RuntimeException("Invalid Unicode escape.");
                        }
                        source.append((char) Integer.parseInt(
                            body.substring(index, index + 4), 16
                        ));
                        index += 4;
                        break;

                    default:
                        throw new RuntimeException("Invalid JSON escape.");
                }

            } else {
                source.append(c);
            }
        }

        throw new RuntimeException("Invalid source JSON.");
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
