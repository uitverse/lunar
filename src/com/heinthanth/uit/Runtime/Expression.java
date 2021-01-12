package com.heinthanth.uit.Runtime;

import com.heinthanth.uit.Lexer.Token;

public abstract class Expression {

    public interface Visitor<R> {
        R visitBinaryExpression(BinaryExpression expression);
        R visitGroupingExpression(GroupingExpression expression);
        R visitLiteralExpression(LiteralExpression expression);
        R visitUnaryExpression(UnaryExpression expression);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static class BinaryExpression extends Expression {
        public BinaryExpression(Expression left, Token operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpression(this);
        }

        public final Expression left;
        public final Token operator;
        public final Expression right;
    }

    public static class GroupingExpression extends Expression {
        public GroupingExpression(Expression expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpression(this);
        }

        public final Expression expression;
    }

    public static class LiteralExpression extends Expression {
        public LiteralExpression(Token value) {
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpression(this);
        }

        public final Token value;
    }

    public static class UnaryExpression extends Expression {
        public UnaryExpression(Token operator, Expression right) {
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpression(this);
        }

        public final Token operator;
        public final Expression right;
    }

}
