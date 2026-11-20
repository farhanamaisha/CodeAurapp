
import { useState } from "react";
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
using namespace std;

int main() {
    int a = 10;
    int b = 20;
    cout << a + b << endl;
    return 0;
}`
};

function getErrorCategory(message) {
    const error = String(message).toLowerCase();

    if (/lexical|illegal character|invalid token|unrecognized token/.test(error))
        return "Lexical Error";
    if (/runtime|division by zero|no more input|invalid int|invalid float/.test(error))
        return "Runtime Error";
    if (/semantic|undeclared|already declared|type mismatch|incompatible/.test(error))
        return "Semantic Error";
    if (/syntax|parse|unexpected token|\bexpected\b|can't recover/.test(error))
        return "Syntax Error";
    if (/build|maven|compilation failed|internal/.test(error))
        return "Build / Internal Error";

    return "Compiler Error";
}

function Phase({ number, title, status, children }) {
    return (
        <div className="panel">
            <div className="phase-heading">
                <h3>{number}. {title}</h3>
                <span className={status === "Completed" ? "success" : "pending"}>
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
    const completed = result?.success === true;

    return (
        <main className="app">
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

                <textarea
                    className="code-editor"
                    spellCheck="false"
                    value={source}
                    onChange={e => setSource(e.target.value)}
                    placeholder={`Write ${language} code here...`}
                />
            </section>

            <section className="editor-section">
                <h2>Standard Input</h2>
                <p className="hint">
                    Enter the values your Scanner statements will read.
                    Separate values with spaces or new lines.
                </p>

                <textarea
                    className="input-editor"
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    placeholder={"Example:\n25\nFarhana"}
                />
            </section>

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

                    {errors.length > 0 && (
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
                        </div>
                    )}

                    {runtimeErrors.length > 0 && (
                        <div className="panel error-panel">
                            <h3>Runtime Diagnostics</h3>
                            {runtimeErrors.map((error, index) => (
                                <div className="diagnostic" key={index}>
                                    <strong>Runtime Error</strong>
                                    <p>{String(error)}</p>
                                </div>
                            ))}
                        </div>
                    )}

                    <Phase
                        number="1"
                        title="Lexical Analysis"
                        status={result.tokens ? "Completed" : "Not completed"}
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

                    <Phase
                        number="2"
                        title="Syntax Analysis"
                        status={result.ast ? "Completed" : "Not completed"}
                    >
                        {result.ast ? (
                            <pre>{JSON.stringify(result.ast, null, 2)}</pre>
                        ) : (
                            <p>AST not generated.</p>
                        )}
                    </Phase>

                    <Phase
                        number="3"
                        title="Semantic Analysis"
                        status={result.symbols ? "Completed" : "Not completed"}
                    >
                        {result.symbols?.length ? (
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
                                {result.symbols
                                    ? "No variables declared."
                                    : "Semantic analysis not completed."}
                            </p>
                        )}
                    </Phase>

                    <Phase
                        number="4"
                        title="Intermediate Code Generation"
                        status={result.tac ? "Completed" : "Not completed"}
                    >
                        {result.tac?.length > 0 ? (
                            <pre>{result.tac.join("\n")}</pre>
                        ) : (
                            <p>No intermediate code generated.</p>
                        )}
                    </Phase>

                    <Phase
                        number="5"
                        title="Code Optimization"
                        status={result.optimized_tac ? "Completed" : "Not implemented"}
                    >
                        {result.optimized_tac ? (
                            <pre>
                                {result.optimized_tac.length > 0
                                    ? result.optimized_tac.join("\n")
                                    : "No optimized TAC generated."}
                            </pre>
                        ) : (
                            <p>Optimization phase is pending backend implementation.</p>
                        )}
                    </Phase>

                    <Phase
                        number="6"
                        title="Code Generation"
                        status={result.code ? "Completed" : "Not implemented"}
                    >
                        {result.code ? (
                            <pre>
                                {result.code.length > 0
                                    ? result.code.join("\n")
                                    : "No generated code."}
                            </pre>
                        ) : (
                            <p>Target code generation is pending backend implementation.</p>
                        )}
                    </Phase>
                </section>
            )}

            <section className="panel">
                <h3>Program Output</h3>
                <pre className="output-placeholder">
                    {!result
                        ? `Compile your ${language} program to view its output here.`
                        : runtimeErrors.length > 0
                            ? "Program execution failed. See Runtime Diagnostics above."
                            : programOutput.length > 0
                                ? programOutput.join("\n")
                                : completed
                                    ? "Program executed successfully. No output."
                                    : "Program output is unavailable because compilation failed."}
                </pre>
            </section>

            <footer className="footer">
                {language} Compiler · Compiler Construction
            </footer>
        </main>
    );
}
