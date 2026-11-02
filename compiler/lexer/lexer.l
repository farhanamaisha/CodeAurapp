cat > compiler/lexer/lexer.l <<'EOF'
%{
#include <stdio.h>
#include <stdlib.h>
#include "parser.tab.h"
%}

%option noyywrap yylineno

DIGIT       [0-9]
LETTER      [a-zA-Z_]
IDENTIFIER  {LETTER}({LETTER}|{DIGIT})*
INTEGER     {DIGIT}+
FLOAT       {DIGIT}+"."{DIGIT}+

%%

"int"       { return INT; }
"float"     { return FLOAT_TYPE; }
"bool"      { return BOOL; }

"if"        { return IF; }
"else"      { return ELSE; }
"while"     { return WHILE; }
"print"     { return PRINT; }

"true"      { return TRUE; }
"false"     { return FALSE; }

"&&"        { return AND; }
"||"        { return OR; }
"!"         { return NOT; }

"<="        { return LE; }
">="        { return GE; }
"=="        { return EQ; }
"!="        { return NE; }
"<"         { return LT; }
">"         { return GT; }

"+"         { return PLUS; }
"-"         { return MINUS; }
"*"         { return MULTIPLY; }
"/"         { return DIVIDE; }
"%"         { return MODULO; }

"="         { return ASSIGN; }

"{"         { return LBRACE; }
"}"         { return RBRACE; }
"("         { return LPAREN; }
")"         { return RPAREN; }
";"         { return SEMICOLON; }

{FLOAT}     { return FLOAT_LITERAL; }
{INTEGER}   { return INTEGER_LITERAL; }
{IDENTIFIER} { return IDENTIFIER_TOKEN; }

[ \t\r\n]+  { /* Ignore whitespace */ }

. {
    fprintf(stderr,
        "Lexical error at line %d: Invalid character '%s'\n",
        yylineno, yytext);
    return INVALID_TOKEN;
}

%%
EOF