package codeanalysis.syntax;

/**
 * The {@code StatementSyntax} class is an abstract base class for representing statement syntax nodes in the syntax tree.
 * It extends the {@link SyntaxNode} class and serves as the base class for different types of statements.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public abstract class StatementSyntax extends SyntaxNode {
    private boolean _isPublic = false;

    /**
     * Whether this declaration was written {@code pub}, and so is part of what
     * its module exports.
     *
     * <p>The flag lives on the base class because {@code pub} is a prefix on
     * every kind of declaration — a function, a struct, an enum, a sum type, an
     * actor, a module-level variable — and threading it through six
     * constructors would say the same thing six times.
     *
     * @return true when the declaration is exported.
     */
    public boolean isPublic() {
        return _isPublic;
    }

    /** Marks this declaration as exported by its module. */
    public void markPublic() {
        _isPublic = true;
    }
}
