
import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import ast.ASTNode;
import ast.ASTPrinter;
import semantic.SemanticAnalyzer;
import symboltable.Symbol;
import intermediate.TACGenerator;

import java.util.List;

public class Main {

    public static void main(String[] args) {

        String source = """
            int age = 20;
            int total = 0;

            if (age > 18) {
                print(age);
            }

            for (int i = 0; i < 5; i = i + 1) {
                total = total + i;
            }

            print(total);
            """;

        try {
            // 1. Lexical Analysis
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.tokenize();

            System.out.println("\\n===== TOKENS =====");
            for (Token token : tokens) {
                System.out.println(token);
            }

            // 2. Syntax Analysis
            Parser parser = new Parser(tokens);
            ASTNode ast = parser.parse();

            System.out.println("\\n===== AST =====");
            ASTPrinter.print(ast);

            // 3. Semantic Analysis
            SemanticAnalyzer semantic = new SemanticAnalyzer();
            List<String> errors = semantic.analyze(ast);

            System.out.println("\\n===== SEMANTIC ANALYSIS =====");

            if (errors.isEmpty()) {
                System.out.println("No semantic errors.");
            } else {
                errors.forEach(System.out::println);
                return;
            }

            // 4. Symbol Table
            System.out.println("\\n===== SYMBOL TABLE =====");
            for (Symbol symbol : semantic.getSymbolTable().getAllSymbols()) {
                System.out.println(symbol);
            }

            // 5. Intermediate Code Generation
            TACGenerator tac = new TACGenerator();
            List<String> instructions = tac.generate(ast);

            System.out.println("\\n===== THREE ADDRESS CODE =====");
            instructions.forEach(System.out::println);

        } catch (RuntimeException e) {
            System.out.println("Compiler Error: " + e.getMessage());
        }
    }
}
