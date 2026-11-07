
package ast;

import java.util.ArrayList;
import java.util.List;

public class ASTNode {

    private final String type;
    private final String value;
    private final int line;
    private final List<ASTNode> children = new ArrayList<>();

    public ASTNode(String type, String value, int line) {
        this.type = type;
        this.value = value;
        this.line = line;
    }

    public void addChild(ASTNode child) {
        children.add(child);
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getLine() {
        return line;
    }

    public List<ASTNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return type + (value == null ? "" : ": " + value);
    }
}
