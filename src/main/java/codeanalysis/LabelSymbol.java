package codeanalysis;

/**
 * Represents a label symbol in the code analysis process.
 * A label symbol is a symbol that represents a label.
 * It is used to jump to a label.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class LabelSymbol {
    private final String _name;
    public LabelSymbol(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }
}
