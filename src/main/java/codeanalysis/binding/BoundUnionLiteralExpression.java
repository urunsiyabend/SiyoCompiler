package codeanalysis.binding;

import codeanalysis.SiyoUnion;
import codeanalysis.UnionSymbol;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents the construction of a sum type value: {@code Ok(5)}, {@code None}.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundUnionLiteralExpression extends BoundExpression {
    private final UnionSymbol _unionType;
    private final String _variantName;
    private final List<BoundExpression> _arguments;

    public BoundUnionLiteralExpression(UnionSymbol unionType, String variantName, List<BoundExpression> arguments) {
        _unionType = unionType;
        _variantName = variantName;
        _arguments = arguments;
    }

    /**
     * Gets the sum type being constructed.
     *
     * @return The union type.
     */
    public UnionSymbol getUnionType() {
        return _unionType;
    }

    /**
     * Gets the name of the variant being constructed.
     *
     * @return The variant name.
     */
    public String getVariantName() {
        return _variantName;
    }

    /**
     * Gets the payload expressions, in declaration order.
     *
     * @return The payload arguments.
     */
    public List<BoundExpression> getArguments() {
        return _arguments;
    }

    @Override
    public BoundNodeType getType() {
        return BoundNodeType.UnionLiteralExpression;
    }

    @Override
    public Class<?> getClassType() {
        return SiyoUnion.class;
    }

    @Override
    public Iterator<BoundNode> getChildren() {
        return Collections.emptyIterator();
    }
}
