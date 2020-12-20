package com.heinthanth.uit.Interpreter;

import com.heinthanth.uit.Lexer.Token;
import com.heinthanth.uit.Lexer.TokenType;
import com.heinthanth.uit.Node.*;
import com.heinthanth.uit.Uit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter implements Expression.Visitor<Object>, Statement.Visitor<Void> {
    /**
     * interrupt to break loop
     */
    private static class BreakException extends RuntimeException {
    }

    /**
     * visit to literal expression -> Java Object (double, string, bool)
     */
    @Override
    public Object visitLiteralExpression(Expression.LiteralExpression expression) {
        return expression.value;
    }

    /**
     * visit grouping expression -> recursive evaluation
     */
    @Override
    public Object visitGroupingExpression(Expression.GroupingExpression expression) {
        return evaluate(expression.expression);
    }

    /**
     * evaluate unary expression to value
     */
    @Override
    public Object visitUnaryExpression(Expression.UnaryExpression expression) {
        Object right = evaluate(expression.right);
        switch (expression.operator.type) {
            case MINUS:
                checkNumberOperand(expression.operator, right);
                return -(double) right;
            case NOT:
                return !isTrue(right);
        }
        return null;
    }

    /**
     * Get variable value from identifier
     *
     * @param expression variable access expression
     * @return value of variable
     */
    @Override
    public Object visitVariableExpression(Expression.VariableExpression expression) {
        return lookUpVariable(expression.name, expression);
    }

    private Object lookUpVariable(Token name, Expression expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.sourceString);
        } else {
            return globals.get(name);
        }
    }

    /**
     * evaluate binary operation
     *
     * @param expression binary expression
     * @return result from expression
     */
    @Override
    public Object visitBinaryExpression(Expression.BinaryExpression expression) {
        Object left = evaluate(expression.left);
        Object right = evaluate(expression.right);

        switch (expression.operator.type) {
            case PLUS:
                return concatOrPlus(left, expression.operator, right);
//                checkNumberOperands(expression.operator, left, right);
//                return (double) left + (double) right;
            case MINUS:
                checkNumberOperands(expression.operator, left, right);
                return (double) left - (double) right;
            case SLASH:
                checkNumberOperands(expression.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expression.operator, left, right);
                return (double) left * (double) right;
            case GREATER:
                checkNumberOperands(expression.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expression.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expression.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expression.operator, left, right);
                return (double) left <= (double) right;
            case NOT_EQUAL:
                return !isEqual(left, right);
            case EQUAL:
                return isEqual(left, right);
        }
        return null;
    }

    private Object concatOrPlus(Object left, Token operator, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return (double) left + (double) right;
        }
        if (left instanceof String && right instanceof String) {
            return (String) left + (String) right;
        }
        if (left instanceof String && right instanceof Double) {
            if (
                    ((double) right == Math.floor((double) right))
                            && !Double.isInfinite((double) right)
            ) {
                double r = (double) right;
                return (String) left + (int) r;
            }
            return (String) left + (Double) right;
        }
        if (left instanceof Double && right instanceof String) {
            if (
                    ((double) left == Math.floor((double) left))
                            && !Double.isInfinite((double) left)
            ) {
                double l = (double) left;
                return (int) l + (String) right;
            }
            return (Double) left + (String) right;
        }
        throw new RuntimeError(operator,
                "Cannot call non-function.");
    }

    /**
     * Get true/false from Logical expression
     */
    @Override
    public Object visitLogicalExpression(Expression.LogicalExpression expression) {
        Object left = evaluate(expression.left);
        if (expression.operator.type == TokenType.OR) {
            if (isTrue(left)) return left;
        } else {
            if (!isTrue(left)) return left;
        }
        return evaluate(expression.right);
    }

    /**
     * visit function call
     */
    @Override
    public Object visitCallExpression(Expression.CallExpression expression) {
        Object callee = evaluate(expression.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expression argument : expression.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof UitCallable)) {
            throw new RuntimeError(expression.paren,
                    "Cannot call non-function.");
        }

        UitCallable function = (UitCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expression.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    /**
     * interpret expression statement
     *
     * @param statement expression statement
     * @return null
     */
    @Override
    public Void visitExpressionStatement(Statement.ExpressionStatement statement) {
        evaluate(statement.expression);
        return null;
    }

    /**
     * interpret output statement
     *
     * @param stmt Output statement
     * @return null
     */
    @Override
    public Void visitOutputStatement(Statement.OutputStatement stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    /**
     * Declare variable
     *
     * @param stmt variable declaration statement
     * @return null
     */
    @Override
    public Void visitVariableDeclareStatement(Statement.VariableDeclareStatement stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.typeDef, stmt.name, value);
        return null;
    }

    /**
     * assign variable with new value
     *
     * @param expr variable declaration statement
     * @return null
     */
    @Override
    public Void visitVariableAssignExpression(Expression.VariableAssignExpression expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return null;
    }

    /**
     * visit block statement
     *
     * @param statement block statement
     * @return null
     */
    @Override
    public Void visitBlockStatement(Statement.BlockStatement statement) {
        executeBlock(statement.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStatement(Statement.IfStatement statement) {
        for (Map.Entry<Expression, Statement> stmt : statement.branches.entrySet()) {
            if (isTrue(evaluate(stmt.getKey()))) {
                execute(stmt.getValue());
                return null;
            }
        }
        if (statement.elseBranch != null) {
            execute(statement.elseBranch);
        }
        return null;
    }

    /**
     * interpret while statement
     */
    @Override
    public Void visitWhileStatement(Statement.WhileStatement statement) {

        try {
            while (isTrue(evaluate(statement.condition))) {
                execute(statement.body);
            }
        } catch (BreakException e) {
            // nothing
        }
        return null;
    }

    /**
     * Interpret function declaration
     */
    @Override
    public Void visitFunctionStatement(Statement.FunctionStatement statement) {
        UitFunction function = new UitFunction(statement, environment);
        environment.define(statement.name.sourceString, function);
        return null;
    }

    /**
     * Interpret return statement
     */
    @Override
    public Void visitReturnStatement(Statement.ReturnStatement statement) {
        Object value = null;
        if (statement.value != null) value = evaluate(statement.value);
        throw new FunctionReturn(value);
    }

    @Override
    public Void visitBreakStatement(Statement.BreakStatement stmt) {
        throw new BreakException();
    }

    /**
     * Environment for identifier
     */
    public final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expression, Integer> locals = new HashMap<>();

    /**
     * Interpreter constructor
     */
    public Interpreter() {
        globals.define("time", new UitCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                return Instant.now().getEpochSecond();
            }

            @Override
            public String toString() {
                return "<native func>";
            }
        });
    }

    /**
     * Interpret parse statements
     *
     * @param statements Statements to interpret
     */
    public void interpret(List<Statement> statements) {
        try {
            for (Statement statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Uit.runtimeError(error);
        }
    }

    void resolve(Expression expr, int depth) {
        locals.put(expr, depth);
    }

    /**
     * Execute statements
     *
     * @param stmt Statement to execute
     */
    private void execute(Statement stmt) {
        stmt.accept(this);
    }

    /**
     * Execute list of statements
     *
     * @param statements  list of statements to execute
     * @param environment current environment
     */
    public void executeBlock(List<Statement> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Statement statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    /**
     * Evaluate expression to value
     *
     * @return value of expression
     */
    private Object evaluate(Expression expression) {
        return expression.accept(this);
    }

    /**
     * Get boolean representation and check true
     *
     * @param object object to check
     */
    private boolean isTrue(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    /**
     * check give object are equal
     *
     * @param a Object one
     * @param b Object two
     * @return true if object are equal
     */
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    /**
     * checking for number in unary operation
     *
     * @param operator Operator of expression
     * @param operand  Operand
     */
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    /**
     * check number operand for binary operation
     *
     * @param operator operator of expression
     * @param left     left object
     * @param right    right object
     */
    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    /**
     * Convert java object to string
     *
     * @param object object to convert
     * @return string representation of object
     */
    private String stringify(Object object) {
        if (object == null) return "null";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }
}
