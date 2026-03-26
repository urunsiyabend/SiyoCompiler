# Siyo Compiler - Development Roadmap

## Current State

### Language Features
- **Types**: `int`, `bool`, `float`, `string`, arrays (`int[]`), structs, enums, `null`
- **Variables**: `mut`/`imut` declarations, compound assignment (`+=`, `-=`, `*=`, `/=`)
- **Control Flow**: `if`/`else`, `while`, `for`, `for...in`, `break`, `continue`
- **Functions**: `fn name(params) -> Type { body }`, implicit return, forward declarations, recursion
- **Composite Types**: arrays (`[1, 2, 3]`), structs (`struct Point { x: int, y: int }`), enums (`enum Color { Red, Green, Blue }`)
- **Operators**: arithmetic, comparison, logical, bitwise, shift
- **Strings**: literals with escape sequences, concatenation, indexing (`s[0]`)
- **Comments**: `// line comments`
- **Null**: `null` literal with null-safe equality

### Built-in Functions
`len`, `toString`, `parseInt`, `parseFloat`, `toInt`, `toFloat`, `print`, `println`, `input`, `range`, `push`, `substring`, `contains`

### Pipeline
- **Interpreter**: parse → bind → lower → evaluate (full feature support)
- **JVM Compiler**: parse → bind → lower → emit bytecode (`.class` files)
- **CLI**: `siyo run file.siyo` (interpret) | `siyo compile file.siyo` (compile to bytecode)
- **REPL**: interactive loop with diagnostics

### Test Coverage
- **1344+ tests** passing
- Interpreter tests (evaluator, parser, lexer, binder)
- Compilation tests (bytecode output matches interpreter)
- 13 example programs

### Example Programs
- `hello.siyo` - Functions, structs, arrays, string concatenation
- `fizzbuzz.siyo` - Classic FizzBuzz
- `sorting.siyo` - Bubble sort algorithm
- `calculator.siyo` - Expression parser with operator precedence
- `linked_list.siyo` - Linked list traversal with structs
- `stack.siyo` - Stack data structure implementation
- `todo_list.siyo` - Todo manager with enums and struct arrays
- `enums_and_print.siyo` - Enum-driven movement simulation
- `grade_calculator.siyo` - Student grade calculator with for-in loops
- `word_counter.siyo` - Word frequency counter with string processing
- `guess_game.siyo` - Interactive number guessing game

---

## Completed Phases

### Phase 1 - Functions and Control Flow ✅
### Phase 2 - Strings and Core Types ✅
### Phase 3 - Composite Types (Arrays, Structs, Enums) ✅
### Phase 4 - Float, Break/Continue, Forward Declarations ✅
### Phase 5 - JVM Bytecode Generation ✅
### Phase 6 - Language Polish (for-in, +=, null, print, input, range) ✅

---

## Remaining Roadmap

### Phase 7 - Error Handling
- `try`/`catch` or Result type
- Runtime error recovery (array bounds, null access)

### Phase 8 - Module System
- `import` syntax for file-based modules
- Public/private visibility
- Package organization

### Phase 9 - Java Interop
- Call Java classes from Siyo
- Access Java standard library (networking, file I/O, collections)

### Phase 10 - Advanced Type System
- Generics (`Array<int>`, `Map<string, int>`)
- Type inference
- Closures / first-class functions
- Interfaces / traits

### Phase 11 - Tooling
- Formatter (`siyo fmt`)
- LSP for editor integration
- Package manager
- Self-hosting (compiler written in Siyo)

---

## Development Guidelines

### Code Quality
- All new features must have tests
- Compilation tests verify bytecode matches interpreter
- Report diagnostics instead of throwing exceptions

### Architecture
- Interpreter remains as reference implementation
- Bytecode generation is the primary compilation target
- Clean separation: Syntax → Binding → Lowering → Emission/Evaluation
