
package symboltable;

public class Symbol {

    private final String name;
    private final String type;
    private final int line;

    public Symbol(String name, String type, int line) {
        this.name = name;
        this.type = type;
        this.line = line;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return "Name: " + name +
               ", Type: " + type +
               ", Line: " + line;
    }
}
