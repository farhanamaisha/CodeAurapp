package ast;

public class ASTPrinter {

    public static void print(ASTNode node) {
        print(node, 0);
    }

    private static void print(ASTNode node, int depth) {
        System.out.println("  ".repeat(depth) + node);

        for (ASTNode child : node.getChildren()) {
            print(child, depth + 1);
        }
    }
}