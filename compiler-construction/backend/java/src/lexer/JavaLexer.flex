
package lexer;

import java_cup.runtime.Symbol;
import parser.sym;

%%
%public
%class JavaLexer
%unicode
%line
%column
%cup

%state COMMENT

%{
    private Symbol token(int type) {
        return new Symbol(type, yyline + 1, yycolumn + 1, yytext());
    }

    private Symbol keyword(int type) {
        return token(type);
    }
%}

Letter = [a-zA-Z_]
Digit = [0-9]
Identifier = {Letter}({Letter}|{Digit})*
Integer = {Digit}+
Float = {Digit}+"."{Digit}+
StringLiteral = \"([^\"\\]|\\.)*\"

%%

<YYINITIAL> [ \t\f\r\n]+       { /* Ignore whitespace */ }

<YYINITIAL> "//"[^\r\n]*       { /* Ignore single-line comments */ }

<YYINITIAL> "/*"               { yybegin(COMMENT); }
<COMMENT> "*/"                 { yybegin(YYINITIAL); }
<COMMENT> [^*]+                { /* Ignore comment content */ }
<COMMENT> "*"                  { /* Ignore comment character */ }
<COMMENT> <<EOF>>              {
    throw new RuntimeException(
        "Lexical Error: Unterminated block comment at line "
        + (yyline + 1)
    );
}

/* Java class and main keywords */

<YYINITIAL> "class"            { return keyword(sym.CLASS); }
<YYINITIAL> "import"            { return keyword(sym.IMPORT); }
<YYINITIAL> "public"           { return keyword(sym.PUBLIC); }
<YYINITIAL> "private"          { return keyword(sym.PRIVATE); }
<YYINITIAL> "protected"        { return keyword(sym.PROTECTED); }
<YYINITIAL> "static"           { return keyword(sym.STATIC); }
<YYINITIAL> "void"             { return keyword(sym.VOID); }
<YYINITIAL> "main"             { return keyword(sym.MAIN); }

/* Data types */

<YYINITIAL> "int"              { return keyword(sym.INT); }
<YYINITIAL> "float"            { return keyword(sym.FLOAT); }
<YYINITIAL> "double"           { return keyword(sym.DOUBLE); }
<YYINITIAL> "boolean"          { return keyword(sym.BOOLEAN); }
<YYINITIAL> "bool"              { return keyword(sym.BOOLEAN); }
<YYINITIAL> "String"           { return keyword(sym.STRING); }

/* Control flow */

<YYINITIAL> "if"               { return keyword(sym.IF); }
<YYINITIAL> "else"             { return keyword(sym.ELSE); }
<YYINITIAL> "while"            { return keyword(sym.WHILE); }
<YYINITIAL> "for"              { return keyword(sym.FOR); }

/* Scanner input keywords */

<YYINITIAL> "Scanner"          { return keyword(sym.SCANNER); }
<YYINITIAL> "new"              { return keyword(sym.NEW); }
<YYINITIAL> "in"               { return keyword(sym.IN); }
<YYINITIAL> "nextInt"          { return keyword(sym.NEXT_INT); }
<YYINITIAL> "nextFloat"        { return keyword(sym.NEXT_FLOAT); }
<YYINITIAL> "nextLine"         { return keyword(sym.NEXT_LINE); }

/* Output keywords */

<YYINITIAL> "System"           { return keyword(sym.SYSTEM); }
<YYINITIAL> "out"              { return keyword(sym.OUT); }
<YYINITIAL> "println"          { return keyword(sym.PRINTLN); }
<YYINITIAL> "print"            { return keyword(sym.PRINT); }

/* Boolean literals */

<YYINITIAL> "true"|"false"     { return token(sym.BOOLEAN_LITERAL); }

/* Operators */

<YYINITIAL> "&&"               { return keyword(sym.AND); }
<YYINITIAL> "||"               { return keyword(sym.OR); }
<YYINITIAL> "=="               { return keyword(sym.EQUAL); }
<YYINITIAL> "!="               { return keyword(sym.NOT_EQUAL); }
<YYINITIAL> "<="               { return keyword(sym.LESS_EQUAL); }
<YYINITIAL> ">="               { return keyword(sym.GREATER_EQUAL); }
<YYINITIAL> "++"               { return keyword(sym.INCREMENT); }
<YYINITIAL> "--"               { return keyword(sym.DECREMENT); }

<YYINITIAL> "+"                { return keyword(sym.PLUS); }
<YYINITIAL> "-"                { return keyword(sym.MINUS); }
<YYINITIAL> "*"                { return keyword(sym.MULTIPLY); }
<YYINITIAL> "/"                { return keyword(sym.DIVIDE); }
<YYINITIAL> "%"                { return keyword(sym.MODULO); }
<YYINITIAL> "="                { return keyword(sym.ASSIGN); }
<YYINITIAL> "<"                { return keyword(sym.LESS); }
<YYINITIAL> ">"                { return keyword(sym.GREATER); }
<YYINITIAL> "!"                { return keyword(sym.NOT); }

/* Delimiters */

<YYINITIAL> "{"                { return keyword(sym.LBRACE); }
<YYINITIAL> "}"                { return keyword(sym.RBRACE); }
<YYINITIAL> "("                { return keyword(sym.LPAREN); }
<YYINITIAL> ")"                { return keyword(sym.RPAREN); }
<YYINITIAL> "["                { return keyword(sym.LBRACKET); }
<YYINITIAL> "]"                { return keyword(sym.RBRACKET); }
<YYINITIAL> ";"                { return keyword(sym.SEMICOLON); }
<YYINITIAL> ","                { return keyword(sym.COMMA); }
<YYINITIAL> "."                { return keyword(sym.DOT); }

/* Literals and identifiers */

<YYINITIAL> {Float}             { return token(sym.FLOAT_LITERAL); }
<YYINITIAL> {Integer}           { return token(sym.INTEGER_LITERAL); }
<YYINITIAL> {StringLiteral}     { return token(sym.STRING_LITERAL); }
<YYINITIAL> {Identifier}        { return token(sym.IDENTIFIER); }

/* Invalid characters */

<YYINITIAL> . {
    throw new RuntimeException(
        "Lexical Error at line " + (yyline + 1)
        + ", column " + (yycolumn + 1)
        + ": unexpected character '" + yytext() + "'"
    );
}
