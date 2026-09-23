package codeanalysis.binding;

import codeanalysis.SiyoSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A set literal: the elements it was written with, in order.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundSetLiteralExpression extends BoundExpression {
    private final List<BoundExpression> _elements;

    public BoundSetLiteralExpression(List<BoundExpression> elements) {
        _elements = elements;
    }

    /**
     * Gets the elements of the set.
     *
     * @return The bound element expressions.
     */
    public List<BoundExpression> getElements() {
        return _elements;
    }

    @Override
    public BoundNodeType getType() {
        return BoundNodeType.SetLiteralExpression;
    }

    @Override
    public Class<?> getClassType() {
        return SiyoSet.class;
    }

    @Override
    public Iterator<BoundNode> getChildren() {
        return new ArrayList<BoundNode>(_elements).iterator();
    }
}
