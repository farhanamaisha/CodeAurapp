import { useEffect, useRef, useState } from "react";
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

const starterFiles = [
    { id: "main-java", name: "Main.java", language: "Java", folderId: null, content: starterCode.Java },
    { id: "main-c", name: "main.c", language: "C", folderId: null, content: starterCode.C },
    { id: "main-cpp", name: "main.cpp", language: "C++", folderId: null, content: starterCode["C++"] }
];

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
    const [files, setFiles] = useState(starterFiles);
    const [folders, setFolders] = useState([]);
    const [activeFileId, setActiveFileId] = useState("main-java");
    const [input, setInput] = useState("");
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);
    const [outputModalOpen, setOutputModalOpen] = useState(false);
    const [inputSubmitted, setInputSubmitted] = useState(false);
    const lineNumbersRef = useRef(null);
    const lineCount = source.split("\n").length;
    const lineNumbers = Array.from(
        { length: lineCount },
        (_, index) => index + 1
    ).join("\n");

    const hasUserInput =
        language === "Java"
            ? /\bScanner\s*\(|System\.in\b/.test(source)
            : language === "C"
                ? /\bscanf\s*\(/.test(source)
                : /\bcin\s*(?:>>)/.test(source);

    function openFile(file) {
        setActiveFileId(file.id);
        setLanguage(file.language);
        setSource(file.content);
        setInput("");
        setResult(null);
        setInputSubmitted(false);
    }

    function updateActiveFile(content) {
        setSource(content);
        setFiles(currentFiles =>
            currentFiles.map(file =>
                file.id === activeFileId
                    ? { ...file, content }
                    : file
            )
        );
        setResult(null);
        setInputSubmitted(false);
    }

    function createFile() {
        const name = window.prompt("File name (for example: program.c)");
        if (!name?.trim()) return;

        const cleanName = name.trim();
        const extension = cleanName.split(".").pop()?.toLowerCase();
        const fileLanguage =
            extension === "c" ? "C"
                : extension === "cpp" || extension === "cc" ? "C++"
                    : "Java";
        const file = {
            id: `file-${Date.now()}`,
            name: cleanName,
            language: fileLanguage,
            folderId: null,
            content: ""
        };

        setFiles(currentFiles => [...currentFiles, file]);
        openFile(file);
    }

    function createFolder() {
        const name = window.prompt("Folder name");
        if (!name?.trim()) return;
        setFolders(currentFolders => [
            ...currentFolders,
            { id: `folder-${Date.now()}`, name: name.trim() }
        ]);
    }

    function deleteFile(fileId) {
        const file = files.find(item => item.id === fileId);
        if (!file) return;
        if (!window.confirm(`Delete "${file.name}"?`)) return;

        const remainingFiles = files.filter(item => item.id !== fileId);
        setFiles(remainingFiles);

        if (activeFileId === fileId) {
            if (remainingFiles.length > 0) {
                openFile(remainingFiles[0]);
            } else {
                setActiveFileId(null);
                setSource("");
                setInput("");
                setResult(null);
                setInputSubmitted(false);
            }
        }
    }

    function deleteFolder(folderId) {
        const folder = folders.find(item => item.id === folderId);
        if (!folder) return;
        const containedFiles = files.filter(file => file.folderId === folderId);

        const message = containedFiles.length > 0
            ? `Delete "${folder.name}" and ${containedFiles.length} contained file(s)?`
            : `Delete "${folder.name}"?`;
        if (!window.confirm(message)) return;

        const remainingFolders = folders.filter(item => item.id !== folderId);
        const remainingFiles = files.filter(file => file.folderId !== folderId);
        setFolders(remainingFolders);
        setFiles(remainingFiles);

        const activeFileWasDeleted =
            remainingFiles.length !== files.length &&
            !remainingFiles.some(file => file.id === activeFileId);

        if (activeFileWasDeleted) {
            if (remainingFiles.length > 0) {
                openFile(remainingFiles[0]);
            } else {
                setActiveFileId(null);
                setSource("");
                setInput("");
                setResult(null);
                setInputSubmitted(false);
            }
        }
    }

    useEffect(() => {
        if (!outputModalOpen) return undefined;

        function handleEscape(event) {
            if (event.key === "Escape") setOutputModalOpen(false);
        }

        document.addEventListener("keydown", handleEscape);
        return () => document.removeEventListener("keydown", handleEscape);
    }, [outputModalOpen]);

    async function compileCode(runWithInput = false) {
        setOutputModalOpen(true);

        if (hasUserInput && !runWithInput) {
            setInputSubmitted(false);
            setResult(null);
            return;
        }

        setLoading(true);
        setResult(null);
        setInputSubmitted(true);

        try {
            const apiUrl =
                import.meta.env.VITE_API_URL ||
                "/api/compile";

            const response = await fetch(apiUrl, {
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
                <div className="header-actions">
                    <div className="file-tabs" aria-label="Open program files">
                        {files.map(file => (
                            <div
                                key={file.id}
                                className={`file-tab${file.id === activeFileId ? " active" : ""}`}
                            >
                                <button
                                    type="button"
                                    className="open-file-tab"
                                    onClick={() => openFile(file)}
                                >
                                    <span className="file-tab-icon">◻</span>
                                    {file.name}
                                </button>
                                <button
                                    type="button"
                                    className="close-file-tab"
                                    onClick={() => deleteFile(file.id)}
                                    title={`Delete ${file.name}`}
                                    aria-label={`Delete file ${file.name}`}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                    </div>
                    <label className="language-select">
                        Language
                        <select
                            value={language}
                            onChange={event => {
                                const nextLanguage = event.target.value;
                                setLanguage(nextLanguage);
                                setSource(starterCode[nextLanguage]);
                                setFiles(currentFiles =>
                                    currentFiles.map(file =>
                                        file.id === activeFileId
                                            ? {
                                                ...file,
                                                language: nextLanguage,
                                                content: starterCode[nextLanguage]
                                            }
                                            : file
                                    )
                                );
                                setInput("");
                                setResult(null);
                                setInputSubmitted(false);
                            }}
                        >
                            <option value="Java">Java</option>
                            <option value="C">C</option>
                            <option value="C++">C++</option>
                        </select>
                    </label>
                </div>
            </header>

            <div className="workspace-layout">
                <aside className="explorer" aria-label="File explorer">
                    <div className="explorer-heading">
                        <h2>Explorer</h2>
                        <div className="explorer-actions">
                            <button type="button" onClick={createFile} title="New file" aria-label="New file">＋</button>
                            <button type="button" onClick={createFolder} title="New folder" aria-label="New folder">▣</button>
                        </div>
                    </div>

                    <div className="explorer-tree">
                        {folders.map(folder => (
                            <div key={folder.id}>
                                <div className="folder-row">
                                    <span aria-hidden="true">▾</span>
                                    <span className="folder-icon" aria-hidden="true">📁</span>
                                    <span>{folder.name}</span>
                                    <button
                                        type="button"
                                        className="delete-row-button"
                                        onClick={() => deleteFolder(folder.id)}
                                        title={`Delete ${folder.name}`}
                                        aria-label={`Delete folder ${folder.name}`}
                                    >
                                        ×
                                    </button>
                                </div>
                                {files
                                    .filter(file => file.folderId === folder.id)
                                    .map(file => (
                                        <div
                                            key={file.id}
                                            className={`file-row${file.id === activeFileId ? " active" : ""}`}
                                        >
                                            <button
                                                type="button"
                                                className="open-file-button"
                                                onClick={() => openFile(file)}
                                            >
                                                <span aria-hidden="true">◇</span>
                                                <span>{file.name}</span>
                                            </button>
                                            <button
                                                type="button"
                                                className="delete-row-button"
                                                onClick={() => deleteFile(file.id)}
                                                title={`Delete ${file.name}`}
                                                aria-label={`Delete file ${file.name}`}
                                            >
                                                ×
                                            </button>
                                        </div>
                                    ))}
                            </div>
                        ))}

                        {files.filter(file => file.folderId === null).map(file => (
                            <div
                                key={file.id}
                                className={`file-row${file.id === activeFileId ? " active" : ""}`}
                            >
                                <button
                                    type="button"
                                    className="open-file-button"
                                    onClick={() => openFile(file)}
                                >
                                    <span className="file-icon" aria-hidden="true">▧</span>
                                    <span>{file.name}</span>
                                </button>
                                <button
                                    type="button"
                                    className="delete-row-button"
                                    onClick={() => deleteFile(file.id)}
                                    title={`Delete ${file.name}`}
                                    aria-label={`Delete file ${file.name}`}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                    </div>
                </aside>

                <div className="workspace-main">
            {/* SOURCE CODE EDITOR */}

            <section className="editor-section">
                <div className="section-heading">
                    <div>
                        <h2>{language} Source Code</h2>
                        <p className="hint">Write your {language} program below.</p>
                    </div>

                    <button
                        className="run-button"
                        onClick={() => compileCode(false)}
                        disabled={loading || !source.trim()}
                    >
                        {loading ? "Runing..." : `▶ Run ${language}`}
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
                        onChange={e => {
                            updateActiveFile(e.target.value);
                        }}
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
                        <p className="hint">
                            Source code → Tokens
                        </p>
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
                        <p className="hint">
                            Tokens → Abstract Syntax Tree (AST)
                        </p>
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
                        <p className="hint">
                            AST → Annotated AST + Symbol Table
                        </p>
                        {result.symbols?.length > 0 ? (
                            <>
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
                            {result.annotatedAst && (
                                <>
                                    <p className="hint">
                                        Annotated AST
                                    </p>
                                    <pre>{result.annotatedAst}</pre>
                                </>
                            )}
                            </>
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
                        <p className="hint">
                            AST → Three-Address Code (TAC)
                        </p>
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
                        <p className="hint">
                            Intermediate Code → Optimized Intermediate Code
                        </p>
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
                                : language === "C"
                                    ? "Target Code Generation (Assembly)"
                                    : "Target Code Generation"
                        }
                        status={codeGenerationStatus}
                    >
                        <p className="hint">
                            Optimized Code → Assembly / Machine Code
                        </p>
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
            {outputModalOpen && (
                <div
                    className="output-modal-backdrop"
                    role="presentation"
                    onMouseDown={event => {
                        if (event.target === event.currentTarget) {
                            setOutputModalOpen(false);
                        }
                    }}
                >
                    <section
                        className="output-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="output-modal-title"
                    >
                        <div className="output-modal-header">
                            <div>
                                <h2 id="output-modal-title">
                                    Program Console
                                </h2>
                                <p className="hint">
                                    {language} · Standard input and output
                                </p>
                            </div>
                            <button
                                type="button"
                                className="modal-close"
                                onClick={() => setOutputModalOpen(false)}
                                aria-label="Close program console"
                            >
                                ×
                            </button>
                        </div>

                        <div className={`console-grid${hasUserInput ? "" : " console-output-only"}`}>
                            {hasUserInput && (
                                <div className="console-pane">
                                    <div className="console-heading">
                                        <h3>Standard Input</h3>
                                        <span>stdin</span>
                                    </div>
                                    <textarea
                                        className="console-input"
                                        value={input}
                                        onChange={event => {
                                            setInput(event.target.value);
                                            setInputSubmitted(false);
                                            setResult(null);
                                        }}
                                        placeholder={"Example:\n25"}
                                    />
                                    <button
                                        type="button"
                                        className="rerun-button"
                                        onClick={() => compileCode(true)}
                                        disabled={
                                            loading ||
                                            !source.trim() ||
                                            !input.trim()
                                        }
                                    >
                                        {loading ? "Running..." : "Run with Input"}
                                    </button>
                                </div>
                            )}

                            <div className="console-pane">
                                <div className="console-heading">
                                    <h3>Program Output</h3>
                                    <span>stdout</span>
                                </div>
                                <pre className="console-output">
                                    {loading
                                        ? "Compiling and running..."
                                        : hasUserInput && !inputSubmitted
                                            ? "Enter the required input, then run the program."
                                            : !result
                                                ? "No program output yet."
                                                : compilationFailed
                                                    ? "Program execution skipped because compilation failed."
                                                    : hasRuntimeError
                                                        ? "Program execution failed."
                                                        : programOutput.length > 0
                                                            ? programOutput.join("\n")
                                                            : completed
                                                                ? "Program executed successfully. No output."
                                                                : "Program output is unavailable."}
                                </pre>
                            </div>
                        </div>
                    </section>
                </div>
            )}

                </div>
            </div>

            {/* FOOTER */}

            <footer className="footer">
                <div className="footer-brand">
                    <span className="footer-status-dot" aria-hidden="true"></span>
                    <strong>{language} Compiler</strong>
                    <span>Compiler Construction Workspace</span>
                </div>
                <div className="footer-meta">
                    <span>{activeFileId
                        ? files.find(file => file.id === activeFileId)?.name
                        : "No file selected"}</span>
                    <span>UTF-8</span>
                    <span>{lineCount} line{lineCount === 1 ? "" : "s"}</span>
                    <span>Ready</span>
                </div>
            </footer>

        </main>
    );
}