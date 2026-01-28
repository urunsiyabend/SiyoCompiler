# Siyo Compiler - Development Roadmap

This document outlines the current state of the Siyo language/compiler and a technical, phased roadmap to reach a stable core language. IO, networking, and stdlib work are intentionally out of scope for now.

## Current State (Phase 0 Complete)

### Capabilities
- **Syntax**: expressions, blocks, variable declarations (`mut`/`imut`), if/else, while, for loops
- **Types**: `int` (32-bit signed integer) and `bool`
- **Operators**: arithmetic (`+`, `-`, `*`, `/`, `%`), comparison (`<`, `<=`, `>`, `>=`, `==`, `!=`), logical (`&&`, `||`, `!`), bitwise (`&`, `|`, `^`, `~`, `<<`, `>>`)
- **Pipeline**: parse -> bind -> lower -> interpret (Evaluator)
- **REPL**: basic loop with diagnostics
- **Documentation**: `GRAMMAR.md` with complete lexical/syntactic grammar and type rules

### Test Coverage
- **1049 tests** passing
- Parser tests for expressions, operators, and statements
- Binder tests for type checking, scoping, and control flow
- Evaluator tests for computation correctness
- Lexer tests for tokenization

### Recent Improvements (Phase 0)
- Fixed critical bug: removed `System.exit(1)` calls in Binder that terminated JVM on errors
- Cleaned up dead code in `BoundNode.java`
- Improved error messages in `BoundTreeRewriter` (now indicates compiler bugs clearly)
- Created comprehensive grammar documentation
- Added extensive parser statement tests
- Added binder tests for type checking and scoping

## Gaps and Risks
- No functions (definitions/calls/return)
- No strings or composite types (arrays/records)
- No module system or standard library packaging
- Error recovery in parser could be improved
- Tooling is REPL-only; no script runner or build target
- No bytecode generation yet (interpreter only)

---

## Roadmap

### Phase 1 - Functions and Control Flow
**Goal**: Enable real programs beyond single expressions.

**Priority: HIGH** - This is the next major milestone.

#### Tasks
1. **Function Declarations**
   - Syntax: `fn name(param: Type, ...) -> Type { ... }`
   - New AST nodes: `FunctionDeclarationSyntax`, `ParameterSyntax`, `ReturnStatementSyntax`
   - Support for void functions (no return type)

2. **Function Calls**
   - Syntax: `name(arg1, arg2, ...)`
   - New AST node: `CallExpressionSyntax`
   - Argument count and type checking

3. **Symbol Table Enhancement**
   - Function symbol type with parameter types and return type
   - Overload detection (report duplicate function names)
   - Forward declaration support (optional)

4. **Control Flow Analysis**
   - Return statements in function bodies
   - Ensure all code paths return a value (for non-void functions)
   - Unreachable code detection (optional)

5. **Runtime Changes**
   - Call stack with stack frames
   - Local variable storage per frame
   - Return value handling

6. **Diagnostics**
   - Undefined function
   - Wrong argument count
   - Wrong argument types
   - Missing return statement
   - Return type mismatch

#### Acceptance Criteria
```siyo
fn add(a: int, b: int) -> int {
    a + b
}

fn isPositive(n: int) -> bool {
    n > 0
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

### Immediate (Phase 1 Start)
1. Add `FunctionDeclarationSyntax` and `ParameterSyntax` to parser
2. Add `CallExpressionSyntax` for function calls
3. Extend `Binder` with function symbol table
4. Add `ReturnStatementSyntax` parsing and binding
5. Implement call stack in `Evaluator`

### Short-Term
1. Complete Phase 1 (functions)
2. Add basic string support (Phase 2)
3. Consider bytecode generation for performance

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
- IO and networking should wait until functions, strings, and modules are stable
- Keep the interpreter as the reference backend; codegen can be added later
- Consider adding a `--debug` flag for internal tree dumps
