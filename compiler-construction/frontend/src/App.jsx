
import { useState } from "react";
import "./styles/App.css";

const starterCode = `int age = 20;
int total = 0;

if (age > 18) {
    print(age);
}

for (int i = 0; i < 5; i = i + 1) {
    total = total + i;
}

print(total);`;

export default function App() {
  const [source, setSource] = useState(starterCode);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  async function compileCode() {
    setLoading(true);
    setResult(null);

    try {
      const response = await fetch("/api/compile", {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ source })
      });

      if (!response.ok) {
        throw new Error(`Server returned ${response.status}`);
      }

      const data = await response.json();
      setResult(data);

    } catch (error) {
      setResult({
        success: false,
        errors: [
          error.message +
          ". Check that the Java backend is running on port 8080."
        ]
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app">
      <header className="header">
        <div>
          <h1>Mini Language Compiler</h1>
          <p>Compiler Construction Project</p>
        </div>
        <span className="language-tag">Java Backend</span>
      </header>

      <section className="editor-section">
        <div className="section-heading">
          <h2>Source Code</h2>
          <button
            className="run-button"
            onClick={compileCode}
            disabled={loading}
          >
            {loading ? "Compiling..." : "▶ Run Compiler"}
          </button>
        </div>

        <textarea
          className="code-editor"
          spellCheck="false"
          value={source}
          onChange={(event) => setSource(event.target.value)}
          placeholder="Write your mini-language code here..."
        />
      </section>

      {result && (
        <section className="results">
          <div className="result-status">
            <h2>Compiler Results</h2>
            <span className={result.success ? "success" : "failure"}>
              {result.success ? "Compilation successful" : "Compilation failed"}
            </span>
          </div>

          {result.errors?.length > 0 && (
            <div className="panel error-panel">
              <h3>Errors</h3>
              {result.errors.map((error, index) => (
                <p key={index}>{error}</p>
              ))}
            </div>
          )}

          {result.tokens && (
            <div className="panel">
              <h3>Lexical Analysis — Tokens</h3>
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>Type</th>
                      <th>Lexeme</th>
                      <th>Line</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.tokens.map((token, index) => (
                      <tr key={index}>
                        <td>{token.type}</td>
                        <td>{token.lexeme || "EOF"}</td>
                        <td>{token.line}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {result.ast && (
            <div className="panel">
              <h3>Syntax Analysis — Abstract Syntax Tree</h3>
              <pre>{JSON.stringify(result.ast, null, 2)}</pre>
            </div>
          )}

          {result.symbols && (
            <div className="panel">
              <h3>Symbol Table</h3>
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Type</th>
                      <th>Line</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.symbols.map((symbol, index) => (
                      <tr key={index}>
                        <td>{symbol.name}</td>
                        <td>{symbol.type}</td>
                        <td>{symbol.line}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {result.tac && (
            <div className="panel">
              <h3>Intermediate Code — Three Address Code</h3>
              <pre>{result.tac.join("\n") || "No TAC generated."}</pre>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
