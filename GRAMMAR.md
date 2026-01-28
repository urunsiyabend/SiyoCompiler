# Siyo Language Grammar

This document describes the grammar of the Siyo programming language.

## Lexical Grammar

### Keywords

| Keyword | Description |
|---------|-------------|
| `true`  | Boolean literal true |
| `false` | Boolean literal false |
| `mut`   | Mutable variable declaration |
| `imut`  | Immutable variable declaration |
| `if`    | Conditional statement |
| `else`  | Else clause |
| `while` | While loop |
| `for`   | For loop |

### Operators

#### Unary Operators

| Operator | Description |
|----------|-------------|
| `+`      | Unary plus (identity) |
| `-`      | Unary minus (negation) |
| `!`      | Logical NOT |
| `~`      | Bitwise NOT |

#### Binary Operators

| Operator | Description |
|----------|-------------|
| `+`      | Addition |
| `-`      | Subtraction |
| `*`      | Multiplication |
| `/`      | Division |
| `%`      | Modulo |
| `==`     | Equality |
| `!=`     | Inequality |
| `<`      | Less than |
| `<=`     | Less than or equal |
| `>`      | Greater than |
| `>=`     | Greater than or equal |
| `&&`     | Logical AND |
| `\|\|`   | Logical OR |
| `&`      | Bitwise AND |
| `\|`     | Bitwise OR |
| `^`      | Bitwise XOR |
| `<<`     | Left shift |
| `>>`     | Right shift |

#### Assignment Operator

| Operator | Description |
|----------|-------------|
| `=`      | Assignment |

### Delimiters

| Token | Description |
|-------|-------------|
| `(`   | Open parenthesis |
| `)`   | Close parenthesis |
| `{`   | Open brace |
| `}`   | Close brace |

### Literals

- **Number**: Sequence of digits (`0-9`)
- **Boolean**: `true` or `false`
- **Identifier**: Letter followed by letters or digits (`[a-zA-Z][a-zA-Z0-9]*`)

## Operator Precedence

Higher precedence binds tighter. Operators at the same precedence level are left-associative.

| Precedence | Operators | Description |
|------------|-----------|-------------|
| 6 (highest)| `+` `-` `!` `~` (unary) | Unary operators |
| 5          | `*` `/` `%` | Multiplicative |
| 4          | `+` `-` | Additive |
| 3          | `==` `!=` `<` `<=` `>` `>=` | Relational |
| 2          | `&&` `&` `<<` `>>` | Logical/Bitwise AND, Shifts |
| 1 (lowest) | `\|\|` `\|` `^` | Logical/Bitwise OR, XOR |

## Syntactic Grammar

### Compilation Unit

```
compilation_unit
    : statement EOF
    ;
```

### Statements

```
statement
    : block_statement
    | variable_declaration
    | if_statement
    | while_statement
    | for_statement
    | expression_statement
    ;

block_statement
    : '{' statement* '}'
    ;

variable_declaration
    : ('mut' | 'imut') IDENTIFIER '=' expression
    ;

if_statement
    : 'if' expression statement else_clause?
    ;

else_clause
    : 'else' statement
    ;

while_statement
    : 'while' expression statement
    ;

for_statement
    : 'for' statement expression expression statement
    ;

expression_statement
    : expression
    ;
```

### Expressions

```
expression
    : assignment_expression
    ;

assignment_expression
    : IDENTIFIER '=' expression
    | binary_expression
    ;

binary_expression
    : unary_expression (binary_operator unary_expression)*
    ;

unary_expression
    : unary_operator unary_expression
    | primary_expression
    ;

primary_expression
    : literal_expression
    | name_expression
    | parenthesized_expression
    ;

literal_expression
    : NUMBER
    | 'true'
    | 'false'
    ;

name_expression
    : IDENTIFIER
    ;

parenthesized_expression
    : '(' expression ')'
    ;
```

## Type System

### Built-in Types

| Type | Java Mapping | Description |
|------|--------------|-------------|
| `int` | `Integer` | 32-bit signed integer |
| `bool` | `Boolean` | Boolean value |

### Type Rules

1. **Literal Types**
   - Number literals have type `int`
   - `true` and `false` have type `bool`

2. **Unary Operators**
   - `+`, `-`, `~`: Operand must be `int`, result is `int`
   - `!`: Operand must be `bool`, result is `bool`

3. **Binary Operators**
   - Arithmetic (`+`, `-`, `*`, `/`, `%`): Both operands must be `int`, result is `int`
   - Comparison (`<`, `<=`, `>`, `>=`): Both operands must be `int`, result is `bool`
   - Equality (`==`, `!=`): Both operands must have the same type, result is `bool`
   - Logical (`&&`, `||`): Both operands must be `bool`, result is `bool`
   - Bitwise (`&`, `|`, `^`): Both operands must be the same type (`int` or `bool`), result is the same type
   - Shift (`<<`, `>>`): Both operands must be `int`, result is `int`

4. **Assignment**
   - Right-hand side type must match the variable's declared type
   - Cannot assign to `imut` (immutable) variables

5. **Control Flow**
   - Condition in `if`, `while`, and `for` statements must be `bool`

## Examples

### Variable Declaration

```siyo
mut x = 10          // mutable integer
imut y = true       // immutable boolean
```

### Control Flow

```siyo
// If statement
if x > 5 {
    x = x - 1
}

// If-else statement
if x == 0 {
    y = false
} else {
    y = true
}

// While loop
while x > 0 {
    x = x - 1
}

// For loop
for mut i = 0 i < 10 i = i + 1 {
    x = x + i
}
```

### Expressions

```siyo
// Arithmetic
1 + 2 * 3           // 7 (precedence)
(1 + 2) * 3         // 9 (parentheses)

// Logical
true && false       // false
true || false       // true
!true               // false

// Bitwise
5 & 3               // 1
5 | 3               // 7
5 ^ 3               // 6
~1                  // -2
8 >> 2              // 2
2 << 3              // 16

// Comparison
5 > 3               // true
5 == 5              // true
5 != 3              // true
```

### Scoping

```siyo
{
    mut x = 10
    {
        // Inner x shadows outer x
        imut x = true
        x               // true
    }
    x                   // 10
}
```
