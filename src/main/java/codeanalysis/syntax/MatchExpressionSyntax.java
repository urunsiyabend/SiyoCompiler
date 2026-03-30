package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * match expr { value1 => body1, value2 => body2, _ => default }
 */
public class MatchExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _matchKeyword;
    private final ExpressionSyntax _target;
    private final List<MatchArmSyntax> _arms;

    public MatchExpressionSyntax(SyntaxToken matchKeyword, ExpressionSyntax target, List<MatchArmSyntax> arms) {
        _matchKeyword = matchKeyword;
        _target = target;
        _arms = arms;
    }

    public SyntaxToken getMatchKeyword() { return _matchKeyword; }
    public ExpressionSyntax getTarget() { return _target; }
    public List<MatchArmSyntax> getArms() { return _arms; }

    @Override
    public SyntaxType getType() { return SyntaxType.MatchExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < 2 + _arms.size(); }
            @Override public SyntaxNode next() {
                if (idx == 0) { idx++; return _matchKeyword; }
                if (idx == 1) { idx++; return _target; }
                int armIdx = idx - 2; idx++; return _arms.get(armIdx);
            }
        };
    }
}
