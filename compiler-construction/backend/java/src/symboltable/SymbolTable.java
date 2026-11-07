
package symboltable;

import java.util.*;

public class SymbolTable {

    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();

    public SymbolTable() {
        enterScope();
    }

    public void enterScope() {
        scopes.push(new LinkedHashMap<>());
    }

    public void exitScope() {
        if (scopes.size() > 1) {
            scopes.pop();
        }
    }

    public boolean declare(Symbol symbol) {
        Map<String, Symbol> currentScope = scopes.peek();

        if (currentScope.containsKey(symbol.getName())) {
            return false;
        }

        currentScope.put(symbol.getName(), symbol);
        return true;
    }

    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }

        return null;
    }

    public Map<String, Symbol> getCurrentScope() {
        return Collections.unmodifiableMap(scopes.peek());
    }

    public List<Symbol> getAllSymbols() {
        List<Symbol> symbols = new ArrayList<>();

        for (Map<String, Symbol> scope : scopes) {
            symbols.addAll(scope.values());
        }

        return symbols;
    }
}
