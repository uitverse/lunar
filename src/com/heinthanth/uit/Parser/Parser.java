package com.heinthanth.uit.Parser;

import java.util.ArrayList;
import java.util.List;

import com.heinthanth.uit.Lexer.Token;
import com.heinthanth.uit.Lexer.token_t;
import com.heinthanth.uit.Runtime.Expression;
import com.heinthanth.uit.Runtime.Statement;
import com.heinthanth.uit.Utils.ErrorHandler;
import static com.heinthanth.uit.Lexer.token_t.*;

class ParseError extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = -9119994735797982514L;
};

public class Parser {

    // tokenizer ကနေ ရလာတဲ့ token array
    private final List<Token> tokens;

    // error handle လုပ်ဖို့ handler
    private ErrorHandler errorHandler;

    // parser cursor ရဲ့ position
    private int current = 0;

    // parser constructor
    public Parser(List<Token> tokens, ErrorHandler errorHandler) {
        this.tokens = tokens;
        this.errorHandler = errorHandler;
    }

    // token တွေကို parse မယ်။
    public List<Statement> parse() {
        List<Statement> statements = new ArrayList<>();
        while (!isEOF()) {
            statements.add(declaration());
        }
        return statements;
    }

    // grammar.txt မှာရေးထားတဲ့အတိုင်း precedence တွေနဲ့ parse သွားမယ်။
    // အရင်ဆုံး declaration

    // var declaration ဘာညာစစ်မယ်။
    private Statement declaration() {
        try {
            if (match(VT_NUMBER, VT_STRING, VT_BOOLEAN))
                return variableDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    /**
     * variable အသစ်ကို declare လုပ်မယ်။
     */
    private Statement variableDeclaration() {
        Token type = previous();
        Token identifier = expect(IDENTIFIER, "Expect variable identifier.");

        Expression initializer = null;
        if (match(ASSIGN)) {
            initializer = expression();
        }
        expect(SEMICOLON, "Expect ';' after statement.");

        return new Statement.VariableDeclarationStatement(type, identifier, initializer);
    }

    /**
     * ရှိပြီးသား variable ကို value အသစ်ထည့်မယ်။
     *
     * @return
     */
    private Statement variableAssignment() {
        Token identifier = expect(IDENTIFIER, "Expect variable identifier.");
        expect(ASSIGN, "Expect '=' in variable assignment.");
        Expression value = expression();
        expect(SEMICOLON, "Expect ';' after statement.");
        return new Statement.VariableAssignStatement(identifier, value);
    }

    // statement တွေစစ်မယ်။ မဟုတ်ရင် expression statement ပေါ့။
    private Statement statement() {
        if (match(OUTPUT))
            return outputStatement();
        if (match(SET))
            return variableAssignment();
        // ကျန်တာကတော့ expression ပေါ့။
        return expressionStatement();
    }

    // output statement parse မယ်
    private Statement outputStatement() {
        Expression value = expression();
        expect(SEMICOLON, "Missing ';' after statement");
        return new Statement.OutputStatement(value);
    }

    // expression statement
    private Statement expressionStatement() {
        Expression expression = expression();
        expect(SEMICOLON, "Missing ';' after statement");
        return new Statement.ExpressionStatement(expression);
    }

    // expression ထက် precedence ပုိမြင့်တာက equal (==, !=)
    private Expression expression() {
        return equal();
    }

    // equal ထက် ပိုမြင့်တာက comparison: သူ့ကို အရင်ရှာမယ်။ ပြီးရင် binary operation
    // လုပ်မယ်။
    private Expression equal() {
        Expression left = comparison();
        // ==, != ရှိမရှိ ... ရှိရင် binary operation ေပါ့ မဟုတ်ရင် ကျန် node
        // အတိုင်းပေါ့။
        while (match(NOT_EQUAL, EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            left = new Expression.BinaryExpression(left, operator, right);
        }
        return left;
    }

    // comparison ထက်မြင့်တာက term
    private Expression comparison() {
        Expression left = term();
        // >, <, >=, <= ရှိမရှိ ... ရှိရင် binary operation ေပါ့ မဟုတ်ရင် ကျန် node
        // အတိုင်းပေါ့။
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expression right = term();
            left = new Expression.BinaryExpression(left, operator, right);
        }
        return left;
    }

    // term ထက်မြင့်တာ factor
    private Expression term() {
        Expression left = factor();
        // +, -, . ရှိမရှိ ... ရှိရင် binary operation ေပါ့ မဟုတ်ရင် ကျန် node
        // အတိုင်းပေါ့။
        while (match(MINUS, PLUS, DOT)) {
            Token operator = previous();
            Expression right = factor();
            left = new Expression.BinaryExpression(left, operator, right);
        }
        return left;
    }

    // factor ထက်မြင့်တာ power
    private Expression factor() {
        Expression left = power();
        // / * ရှိမရှိ ... ရှိရင် binary operation ေပါ့ မဟုတ်ရင် ကျန် node
        // အတိုင်းပေါ့။
        while (match(PERCENT, SLASH, STAR)) {
            Token operator = previous();
            Expression right = power();
            left = new Expression.BinaryExpression(left, operator, right);
        }
        return left;
    }

    // power ထက်မြင့်တာ unary
    private Expression power() {
        Expression left = unary();
        // ^ ရှိမရှိ ... ရှိရင် binary operation ေပါ့ မဟုတ်ရင် ကျန် node
        // အတိုင်းပေါ့။
        while (match(CARET)) {
            Token operator = previous();
            Expression right = unary();
            left = new Expression.BinaryExpression(left, operator, right);
        }
        return left;
    }

    // unary (!, -)
    private Expression unary() {
        // -, ! မရှိရင် primary ဆီသွားမယ်။
        if (match(NOT, MINUS)) {
            Token operator = previous();
            Expression right = unary();
            return new Expression.UnaryExpression(operator, right);
        }
        return primary();
    }

    // ဒါက ထပ်ခွဲမရတော့တဲ့ basic element တွေ literal ဘာညာ
    private Expression primary() {
        if (match(NUMBER_LITERAL, STRING_LITERAL, BOOLEAN_LITERAL))
            return new Expression.LiteralExpression(previous());

        if (match(IDENTIFIER))
            return new Expression.VariableAccessExpression(previous());

        if (match(LEFT_PAREN)) {
            Expression expr = expression();
            expect(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expression.GroupingExpression(expr);
        }

        throw error(getCurrentToken(), "Expect expression.");
    }

    // token type မဟုတ်ရင် error တက်ဖို့
    private Token expect(token_t type, String message) {
        if (check(type))
            return advance();
        throw error(getCurrentToken(), message);
    }

    // parse error တက်ဖို့
    private ParseError error(Token token, String message) {
        errorHandler.reportError(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isEOF()) {
            if (previous().type == SEMICOLON)
                return;
            // new line ဖြစ်တဲ့ token အားလုံး
            switch (getCurrentToken().type) {
                case VT_STRING:
                case VT_NUMBER:
                case VT_BOOLEAN:
                case FRT_VOID:
                case BLOCK:
                case IF:
                case FOR:
                case WHILE:
                case BREAK:
                case CONTINUE:
                case FUNC:
                case RETURN:
                case SET:
                case INPUT:
                case OUTPUT:
                    return;
                default:
                    advance();
                    break;
            }
        }
    }

    // လက်ရှိ token type ကို စစ်ဖို့
    private boolean match(token_t... types) {
        for (token_t type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // သူလည်း token type စစ်ဖို့ပဲ။ match က multiple types
    private boolean check(token_t type) {
        if (isEOF())
            return false;
        return getCurrentToken().type == type;
    }

    // လက်ရှိ token ကို ပြန်ပေးတယ်။ index 1 တိုးတယ်။
    private Token advance() {
        if (!isEOF())
            current++;
        return previous();
    }

    // အဆုံး token ဟုတ် မဟုတ် စစ်ဖို့
    private boolean isEOF() {
        return getCurrentToken().type == EOF;
    }

    // လက်ရှိ token ကို ယူဖို့
    private Token getCurrentToken() {
        return tokens.get(current);
    }

    // အရင် token ကို ကြည့်ဖို့
    private Token previous() {
        return tokens.get(current - 1);
    }
}