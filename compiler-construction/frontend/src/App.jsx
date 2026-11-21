import { useRef, useState } from "react";
import "./styles/App.css";

const starterCode = {
Java: `public class Test {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        int sum = a + b;
        System.out.println(sum);
    }
}`,
C: `#include <stdio.h>

int main(void) {
    int a, b, sum;

    scanf("%d %d", &a, &b);
    sum = a + b;
    printf("%d\\n", sum);

    return 0;
}`,
"C++": `#include <iostream>

int main() {
    int a = 10;
    int b = 20;
    cout << a + b;
    return 0;
}`
};

function getErrorCategory(message) {
    const error = String(message).toLowerCase();

    if (/lexical|illegal character|invalid token|unrecognized token|unexpected character/.test(error))
        return "Lexical Error";

    if (/runtime|division by zero|no more input|invalid int|invalid float/.test(error))
        return "Runtime Error";

    if (/semantic|undeclared|not declared|already declared|type mismatch|incompatible|cannot initialize|cannot assign/.test(error))
        return "Semantic Error";

    if (/syntax|parse|unexpected token|\bexpected\b|can't recover/.test(error))
        return "Syntax Error";

    if (/build|maven|internal|invalid response|could not connect|server error|bytecode generation/.test(error))
        return "Build / Internal Error";

    return "Compiler Error";
}

function Phase({ number, title, status, children }) {
    return (
        <div className="panel">
            <div className="phase-heading">
                <h3>{number}. {title}</h3>

                <span
                    className={
                        status === "Completed"
                            ? "success"
                            : status === "Failed"
                                ? "failure"
                                : "pending"
                    }
                >
                    {status}
                </span>
            </div>

            {children}
        </div>
    );
}

export default function App() {
    const [language, setLanguage] = useState("Java");
    const [source, setSource] = useState(starterCode.Java);
    const [input, setInput] = useState("");
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const lineNumbersRef = useRef(null);
    const lineCount = source.split("\n").length;
    const lineNumbers = Array.from(
        { length: lineCount },
        (_, index) => index + 1
    ).join("\n");

    async function compileCode() {
        setLoading(true);
        setResult(null);

        try {
            const response = await fetch("/api/compile", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ language, source, input })
            });

            const responseText = await response.text();
            let data;

            try {
                data = JSON.parse(responseText);
            } catch {
                throw new Error(
                    `Invalid response from ${language} compiler (HTTP ${response.status}).`
                );
            }

            if (!response.ok) {
                throw new Error(
                    data.errors?.join(", ") ||
                    `Server error: ${response.status}`
                );
            }

            setResult(data);

        } catch (error) {
            setResult({
                success: false,
                tokens: null,
                ast: null,
                symbols: null,
                tac: null,
                optimizedTac: null,
                intermediateCompleted: false,
                optimizationCompleted: false,
                bytecode: null,
                generatedClassFile: null,
                codeGenerationCompleted: false,
                codeGenerationError: null,
                programOutput: [],
                runtimeErrors: [],
                errors: [error.message || `Could not connect to ${language} compiler.`]
            });

        } finally {
            setLoading(false);
        }
    }

    const errors = result?.errors || [];
    const runtimeErrors = result?.runtimeErrors || [];
    const programOutput = result?.programOutput || [];

    // Java backend returns optimizedTac; C/C++ backends return optimized_tac
    const optimizedTac = result?.optimizedTac ?? result?.optimized_tac ?? [];

    const categories = errors.map(getErrorCategory);

    const hasSemanticError = categories.includes("Semantic Error");
    const hasSyntaxError = categories.includes("Syntax Error");
    const hasLexicalError = categories.includes("Lexical Error");

    const hasRuntimeError =
        runtimeErrors.length > 0 ||
        categories.includes("Runtime Error");

    const compilationFailed =
        hasSemanticError ||
        hasSyntaxError ||
        hasLexicalError;

    const completed = result?.success === true;

    const semanticStatus = hasSemanticError
        ? "Failed"
        : result?.symbols !== null && result?.symbols !== undefined
            ? "Completed"
            : "Not completed";

    const intermediateStatus =
        compilationFailed
            ? "Not completed"
            : result?.intermediateCompleted === true ||
              Array.isArray(result?.tac)
                ? "Completed"
                : "Not completed";

    const optimizationStatus =
        compilationFailed
            ? "Not completed"
            : result?.optimizationCompleted === true || optimizedTac.length > 0
                ? "Completed"
                : "Not implemented";

    const codeGenerationStatus =
        compilationFailed
            ? "Not completed"
            : result?.codeGenerationCompleted === true || result?.code?.length > 0
                ? "Completed"
                : result?.codeGenerationError
                    ? "Failed"
                    : "Not implemented";

    return (
        <main className="app">

            {/* HEADER */}

            <header className="header">
                <div>
                    <h1>{language} Compiler</h1>
                    <p>Compiler Construction Project</p>
                </div>

                <label className="language-select">
                    Language
                    <select
                        value={language}
                        onChange={event => {
                            const nextLanguage = event.target.value;
                            setLanguage(nextLanguage);
                            setSource(starterCode[nextLanguage]);
                            setInput("");
                            setResult(null);
                        }}
                    >
                        <option value="Java">Java</option>
                        <option value="C">C</option>
                        <option value="C++">C++</option>
                    </select>
                </label>
            </header>

            {/* SOURCE CODE EDITOR */}

            <section className="editor-section">
                <div className="section-heading">
                    <div>
                        <h2>{language} Source Code</h2>
                        <p className="hint">Write your {language} program below.</p>
                    </div>

                    <button
                        className="run-button"
                        onClick={compileCode}
                        disabled={loading || !source.trim()}
                    >
                        {loading ? "Compiling..." : `▶ Compile ${language}`}
                    </button>
                </div>

                <div className="code-editor-wrapper">
                    <div
                        className="line-numbers"
                        ref={lineNumbersRef}
                        aria-hidden="true"
                    >
                        <pre>{lineNumbers}</pre>
                    </div>
                    <textarea
                        className="code-editor"
                        spellCheck="false"
                        value={source}
                        onChange={e => setSource(e.target.value)}
                        onScroll={e => {
                            if (lineNumbersRef.current) {
                                lineNumbersRef.current.scrollTop =
                                    e.currentTarget.scrollTop;
                            }
                        }}
                        placeholder={`Write ${language} code here...`}
                    />
                </div>
            </section>

            {/* STANDARD INPUT */}

            <section className="editor-section">
                <h2>Standard Input</h2>

                <p className="hint">
                    Enter the values your program will read (Scanner / scanf / cin).
                    Separate values with spaces or new lines.
                </p>

                <textarea
                    className="input-editor"
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    placeholder={"Example:\n25"}
                />
            </section>

            {/* COMPILATION RESULTS */}

            {result && (
                <section className="results">

                    <div className="result-status">
                        <h2>Compilation Results</h2>

                        <span className={completed ? "success" : "failure"}>
                            {completed
                                ? "✓ Compilation Successful"
                                : "✕ Compilation Failed"}
                        </span>
                    </div>

                    {/* DIAGNOSTICS */}

                    {(errors.length > 0 ||
                      runtimeErrors.length > 0 ||
                      result.codeGenerationError) && (
                        <div className="panel error-panel">
                            <h3>Compilation Diagnostics</h3>

                            {errors.map((error, index) => (
                                <div className="diagnostic" key={index}>
                                    <strong>
                                        {getErrorCategory(error)}
                                    </strong>
                                    <p>{String(error)}</p>
                                </div>
                            ))}

                            {runtimeErrors.map((error, index) => (
                                <div
                                    className="diagnostic"
                                    key={`runtime-${index}`}
                                >
                                    <strong>Runtime Error</strong>
                                    <p>{String(error)}</p>
                                </div>
                            ))}

                            {result.codeGenerationError && (
                                <div className="diagnostic">
                                    <strong>Code Generation Error</strong>
                                    <p>{String(result.codeGenerationError)}</p>
                                </div>
                            )}
                        </div>
                    )}

                    {/* PHASE 1: LEXICAL ANALYSIS */}

                    <Phase
                        number="1"
                        title="Lexical Analysis"
                        status={
                            result.tokens !== null &&
                            result.tokens !== undefined
                                ? hasLexicalError
                                    ? "Failed"
                                    : "Completed"
                                : "Not completed"
                        }
                    >
                        {result.tokens ? (
                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Token Type</th>
                                            <th>Lexeme</th>
                                            <th>Line</th>
                                        </tr>
                                    </thead>

                                    <tbody>
                                        {result.tokens.map((token, i) => (
                                            <tr key={i}>
                                                <td>{token.type}</td>
                                                <td>{token.lexeme || "EOF"}</td>
                                                <td>{token.line}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <p>Tokenization did not complete.</p>
                        )}
                    </Phase>

                    {/* PHASE 2: SYNTAX ANALYSIS */}

                    <Phase
                        number="2"
                        title="Syntax Analysis"
                        status={
                            result.ast
                                ? hasSyntaxError
                                    ? "Failed"
                                    : "Completed"
                                : "Not completed"
                        }
                    >
                        {result.ast ? (
                            <pre>
                                {typeof result.ast === "string"
                                    ? result.ast
                                    : JSON.stringify(result.ast, null, 2)}
                            </pre>
                        ) : (
                            <p>AST not generated.</p>
                        )}
                    </Phase>

                    {/* PHASE 3: SEMANTIC ANALYSIS */}

                    <Phase
                        number="3"
                        title="Semantic Analysis"
                        status={semanticStatus}
                    >
                        {result.symbols?.length > 0 ? (
                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Variable</th>
                                            <th>Data Type</th>
                                            <th>Line</th>
                                        </tr>
                                    </thead>

                                    <tbody>
                                        {result.symbols.map((symbol, i) => (
                                            <tr key={i}>
                                                <td>{symbol.name}</td>
                                                <td>{symbol.type}</td>
                                                <td>{symbol.line}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <p>
                                {semanticStatus === "Failed"
                                    ? "Semantic errors detected. See diagnostics above."
                                    : result.symbols
                                        ? "No variables declared."
                                        : "Semantic analysis not completed."}
                            </p>
                        )}
                    </Phase>

                    {/* PHASE 4: INTERMEDIATE CODE GENERATION */}

                    <Phase
                        number="4"
                        title="Intermediate Code Generation"
                        status={intermediateStatus}
                    >
                        {result.tac?.length > 0 ? (
                            <>
                                <p className="hint">
                                    Original Three-Address Code (TAC)
                                </p>

                                <pre>
                                    {result.tac.join("\n")}
                                </pre>
                            </>
                        ) : (
                            <p>No intermediate code generated.</p>
                        )}
                    </Phase>

                    {/* PHASE 5: CODE OPTIMIZATION */}

                    <Phase
                        number="5"
                        title="Code Optimization"
                        status={optimizationStatus}
                    >
                        {optimizedTac.length > 0 ? (
                            <>
                                <p className="hint">
                                    Optimized Three-Address Code (TAC)
                                </p>

                                <pre>
                                    {optimizedTac.join("\n")}
                                </pre>

                                <p className="hint">
                                    Applied optimizations: Constant Folding
                                    and Constant Propagation.
                                </p>
                            </>
                        ) : optimizationStatus === "Completed" ? (
                            <p>
                                Optimization completed. No optimized
                                instructions to display.
                            </p>
                        ) : (
                            <p>
                                {compilationFailed
                                    ? "Optimization skipped because an earlier compilation phase failed."
                                    : "Optimization phase is pending backend implementation."}
                            </p>
                        )}
                    </Phase>

                    {/* PHASE 6: CODE GENERATION */}

                    <Phase
                        number="6"
                        title={
                            language === "Java"
                                ? "Target Code Generation (JVM Bytecode)"
                                : "Code Generation"
                        }
                        status={codeGenerationStatus}
                    >
                        {result.codeGenerationCompleted === true ? (
                            <>
                                <p className="hint">
                                    JVM Bytecode generated successfully.
                                </p>

                                {result.bytecode ? (
                                    <pre className="code-output">
                                        {result.bytecode}
                                    </pre>
                                ) : (
                                    <p>
                                        Bytecode was generated, but its
                                        textual representation is unavailable.
                                    </p>
                                )}

                                {result.generatedClassFile && (
                                    <>
                                        <p className="hint">
                                            Generated Class File
                                        </p>

                                        <pre className="code-output">
                                            {result.generatedClassFile}
                                        </pre>
                                    </>
                                )}
                            </>
                        ) : result.code?.length > 0 ? (
                            <pre>{result.code.join("\n")}</pre>
                        ) : result.codeGenerationError ? (
                            <p>
                                JVM bytecode generation failed. See the
                                diagnostic message above.
                            </p>
                        ) : (
                            <p>
                                {compilationFailed
                                    ? "Code generation skipped because an earlier compilation phase failed."
                                    : "Code generation is pending backend implementation."}
                            </p>
                        )}
                    </Phase>

                </section>
            )}

            {/* PROGRAM OUTPUT */}

            <section className="panel">
                <h3>Program Output</h3>

                <pre className="output-placeholder">
                    {!result
                        ? `Compile your ${language} program to view its output here.`
                        : compilationFailed
                            ? "Program execution skipped because compilation failed."
                            : hasRuntimeError
                                ? "Program execution failed. See Runtime Diagnostics above."
                                : programOutput.length > 0
                                    ? programOutput.join("\n")
                                    : completed
                                        ? "Program executed successfully. No output."
                                        : "Program output is unavailable because compilation failed."}
                </pre>
            </section>

            {/* FOOTER */}

            <footer className="footer">
                {language} Compiler · Compiler Construction
            </footer>

        </main>
    );
}