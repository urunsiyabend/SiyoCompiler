# Siyo Compiler - Development Roadmap

This document outlines the current state of the Siyo language/compiler and a technical, phased roadmap to reach a stable core language. IO, networking, and stdlib work are intentionally out of scope for now.

## Current State (Phase 1 Complete)

### Capabilities
- **Syntax**: expressions, blocks, variable declarations (`mut`/`imut`), if/else, while, for loops, **function declarations, function calls, return statements**
- **Types**: `int` (32-bit signed integer) and `bool`
- **Functions**: `fn name(params) -> Type { body }` with full type checking
- **Operators**: arithmetic (`+`, `-`, `*`, `/`, `%`), comparison (`<`, `<=`, `>`, `>=`, `==`, `!=`), logical (`&&`, `||`, `!`), bitwise (`&`, `|`, `^`, `~`, `<<`, `>>`)
- **Pipeline**: parse -> bind -> lower -> interpret (Evaluator)
- **REPL**: basic loop with diagnostics, now supports function definitions
- **Documentation**: `GRAMMAR.md` with complete lexical/syntactic grammar and type rules

### Test Coverage
- **1049+ tests** passing
- Parser tests for expressions, operators, statements, and functions
- Binder tests for type checking, scoping, control flow, and function validation
- Evaluator tests for computation correctness including function calls
- Lexer tests for tokenization

### Recent Improvements (Phase 1)
- **Function Declarations**: `fn name(param: Type, ...) -> Type { body }`
- **Function Calls**: `name(arg1, arg2)` with argument type checking
- **Return Statements**: `return expr` with return type validation
- **New Tokens**: `fn`, `return`, `:`, `,`, `->`
- **New AST Nodes**: `FunctionDeclarationSyntax`, `ParameterSyntax`, `TypeClauseSyntax`, `ReturnStatementSyntax`, `CallExpressionSyntax`, `SeparatedSyntaxList`
- **New Bound Nodes**: `BoundReturnStatement`, `BoundCallExpression`
- **New Symbols**: `FunctionSymbol`, `ParameterSymbol`
- **Call Stack**: `StackFrame` for function execution with local variable scoping
- **Recursion Support**: Functions can call themselves
- **10 new diagnostic messages** for function-related errors

## Gaps and Risks
- No strings or composite types (arrays/records)
- No module system or standard library packaging
- Error recovery in parser could be improved
- Tooling is REPL-only; no script runner or build target
- No bytecode generation yet (interpreter only)
- No control flow analysis (missing return detection, unreachable code)

---

## Roadmap

### Phase 1 - Functions and Control Flow ✅ COMPLETE
**Goal**: Enable real programs beyond single expressions.

**Status: COMPLETE**

#### Implemented Features
1. **Function Declarations** ✅
   - Syntax: `fn name(param: Type, ...) -> Type { ... }`
   - New AST nodes: `FunctionDeclarationSyntax`, `ParameterSyntax`, `TypeClauseSyntax`
   - Void functions supported (omit `-> Type`)

2. **Function Calls** ✅
   - Syntax: `name(arg1, arg2, ...)`
   - New AST node: `CallExpressionSyntax`
   - Argument count and type checking

3. **Symbol Table Enhancement** ✅
   - `FunctionSymbol` with parameter types and return type
   - `ParameterSymbol` extending `VariableSymbol`
   - Duplicate function detection
   - Function lookup in `BoundScope`

4. **Return Statements** ✅
   - `return expr` for non-void functions
   - `return` for void functions
   - Return type validation

5. **Runtime Changes** ✅
   - `StackFrame` class for call stack
   - Local variable storage per frame
   - Return value handling
   - Recursion support

6. **Diagnostics** ✅
   - `reportUndefinedFunction`
   - `reportWrongArgumentCount`
   - `reportWrongArgumentType`
   - `reportReturnOutsideFunction`
   - `reportReturnWithValueInVoidFunction`
   - `reportMissingReturnValue`
   - `reportReturnTypeMismatch`
   - `reportUndefinedType`
   - `reportDuplicateParameter`
   - `reportFunctionAlreadyDeclared`

#### Not Implemented (Deferred)
- Forward declarations (functions must be declared before use)
- Control flow analysis for missing returns
- Unreachable code detection

#### Verified Example
```siyo
fn add(a: int, b: int) -> int {
    return a + b
}

fn isPositive(n: int) -> bool {
    return n > 0
}

{
    mut result = add(10, 20)
    if isPositive(result) {
        result = result * 2
    }
    result
}
```

---

### Phase 2 - Strings and Core Types
**Goal**: Add practical data handling without IO.

**Priority: HIGH** - This is the next major milestone.

#### Tasks
1. **String Literals**
   - Lexer support for `"string"` syntax
   - Escape sequences: `\n`, `\t`, `\\`, `\"`
   - New `StringToken` type

2. **String Type**
   - Add `string` as third built-in type
   - String literal expressions

3. **String Operations**
   - Concatenation: `"hello" + " " + "world"`
   - Length: built-in `len(s)` function
   - Indexing: `s[0]` returns character (as int or string?)
   - Substring: `s[1:3]` (optional)

4. **Type Conversions**
   - `int` to `string`: `toString(42)` -> `"42"`
   - `bool` to `string`: `toString(true)` -> `"true"`
   - `string` to `int`: `parseInt("42")` -> `42` (with error handling)

#### Acceptance Criteria
```siyo
fn greet(name: string) -> string {
    "Hello, " + name + "!"
}

{
    mut msg = greet("World")
    mut length = len(msg)
    length
}
```

---

### Phase 3 - Composite Types
**Goal**: Support structured data.

#### Tasks
1. **Arrays**
   - Syntax: `[1, 2, 3]` for literals, `int[]` for type
   - Indexing: `arr[0]`
   - Length: `len(arr)`
   - Bounds checking with diagnostics

2. **Records/Structs**
   - Syntax: `struct Point { x: int, y: int }`
   - Field access: `p.x`
   - Construction: `Point { x: 10, y: 20 }`

3. **Optional: Enums**
   - Tagged unions for safer modeling
   - Pattern matching (basic)

#### Acceptance Criteria
```siyo
struct Point {
    x: int
    y: int
}

fn distance(p: Point) -> int {
    p.x * p.x + p.y * p.y
}

{
    mut p = Point { x: 3, y: 4 }
    distance(p)
}
```

---

### Phase 4 - Modules and Packages
**Goal**: Organize code as projects grow.

#### Tasks
1. **File-based Modules**
   - One file = one module
   - Import syntax: `import math` or `import "./utils"`

2. **Namespace Rules**
   - Public/private visibility (default private)
   - Qualified names: `math.sqrt(x)`

3. **Build Model**
   - Single-file compilation (current)
   - Multi-file project compilation
   - Dependency resolution

---

### Phase 5 - Tooling and CLI
**Goal**: Make it usable without a REPL.

#### Tasks
1. **Script Runner**
   - `siyo run script.siyo`
   - Exit codes based on result

2. **Test Runner**
   - `siyo test` to run language tests
   - Test discovery and reporting

3. **Formatter**
   - `siyo fmt script.siyo`
   - Consistent code style

4. **Language Server (Optional)**
   - LSP support for editor integration
   - Syntax highlighting, diagnostics, go-to-definition

---

### Phase 6 - Code Generation (Future)
**Goal**: Compile to executable code.

#### Options
1. **JVM Bytecode** - Already have ASM dependency
2. **LLVM IR** - High performance, cross-platform
3. **Native** - Direct machine code generation

---

## Near-Term Priorities (Next Steps)

### Immediate (Phase 2 Start)
1. Add `StringToken` to lexer with escape sequence handling
2. Add `string` as third built-in type in `Binder.lookupType()`
3. Add string literal expressions (`"hello"`)
4. Implement string concatenation operator (`+`)
5. Add built-in `len(s)` function for string length

### Short-Term
1. Complete Phase 2 (strings)
2. Add string indexing and substring operations
3. Add type conversion functions (`toString`, `parseInt`)
4. Consider bytecode generation for performance

### Optional Enhancements (Phase 1 Follow-up)
1. Control flow analysis for missing return statements
2. Unreachable code detection
3. Forward declaration support

---

## Development Guidelines

### Code Quality
- All new features must have tests
- Use existing patterns (e.g., `AssertingEnumerator` for parser tests)
- Report diagnostics instead of throwing exceptions
- Keep error messages user-friendly

### Architecture
- Keep interpreter as reference implementation
- Codegen can be added later without breaking interpreter
- Maintain clean separation: Syntax -> Binding -> Lowering -> Evaluation

---

## Notes
- IO and networking should wait until strings and modules are stable
- Keep the interpreter as the reference backend; codegen can be added later
- Consider adding a `--debug` flag for internal tree dumps
- Phase 1 functions use explicit `return` statements (implicit last-expression return not implemented)
- Function bodies are stored in `BoundGlobalScope` and passed to `Evaluator` via `Compilation`
- The `SeparatedSyntaxList<T>` generic class can be reused for other comma-separated lists (e.g., array literals)
