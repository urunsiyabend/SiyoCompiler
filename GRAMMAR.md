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
| `fn`    | Function declaration |
| `return`| Return statement |

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
| `:`   | Type annotation separator |
| `,`   | Parameter/argument separator |
| `->`  | Return type indicator |

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
    | function_declaration
    | return_statement
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

function_declaration
    : 'fn' IDENTIFIER '(' parameter_list? ')' type_clause? block_statement
    ;

parameter_list
    : parameter (',' parameter)*
    ;

parameter
    : IDENTIFIER ':' type_identifier
    ;

type_clause
    : '->' type_identifier
    ;

type_identifier
    : 'int'
    | 'bool'
    ;

return_statement
    : 'return' expression?
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
    | call_expression
    | name_expression
    | parenthesized_expression
    ;

literal_expression
    : NUMBER
    | 'true'
    | 'false'
    ;

call_expression
    : IDENTIFIER '(' argument_list? ')'
    ;

argument_list
    : expression (',' expression)*
    ;

name_expression
    : IDENTIFIER
    ;

parenthesized_expression
    : '(' expression ')'
    ;
```

Note: `call_expression` is matched when an identifier is followed by `(`. Otherwise, `name_expression` is matched.

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

6. **Functions**
   - Function parameters have explicit types
   - Return type is specified after `->`, or omitted for void functions
   - Return statement expression type must match function return type
   - Return without expression is only valid in void functions
   - Return with expression is only valid in non-void functions

7. **Function Calls**
   - Number of arguments must match number of parameters
   - Each argument type must match corresponding parameter type
   - Call expression type is the function's return type (or void)

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

### Functions

```siyo
// Simple function with return type
fn add(a: int, b: int) -> int {
    return a + b
}

// Function with boolean return
fn isPositive(n: int) -> bool {
    return n > 0
}

// Void function (no return type)
fn doNothing() {
    return
}

// Function call
{
    mut result = add(10, 20)    // 30
    isPositive(result)          // true
}

// Recursion
fn factorial(n: int) -> int {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

factorial(5)                    // 120
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

### Function Scoping

```siyo
// Functions have their own scope
fn compute(x: int) -> int {
    mut y = x * 2       // y is local to the function
    return y + 1
}

{
    mut y = 100         // This y is separate from function's y
    compute(5)          // Returns 11, does not affect outer y
    y                   // Still 100
}
```
