package org.craftllc.minecraft.mod.cycm.script;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import org.craftllc.minecraft.mod.cycm.CYCMClient;
import org.craftllc.minecraft.mod.cycm.Constants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CYCMScriptEngine {
    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_\\.]*)\\}");

    private CYCMScriptEngine() {
    }

    public static CompletableFuture<ExecutionResult> executeFromTelegram(String author, String scriptName, String source) {
        CompletableFuture<ExecutionResult> future = new CompletableFuture<>();
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            Constants.LOGGER.warn("Cannot execute CYCM script {}, Minecraft client is unavailable", scriptName);
            future.complete(ExecutionResult.failure("Minecraft client is unavailable"));
            return future;
        }
        client.execute(() -> {
            try {
                Program program = new Parser(new Lexer(preprocess(source)).tokenize()).parseProgram();
                RuntimeContext context = new RuntimeContext(author, scriptName);
                program.execute(context);
                future.complete(ExecutionResult.successful());
            } catch (ScriptException e) {
                Constants.LOGGER.warn("CYCM script error in {} from {}: {}", scriptName, author, e.getMessage());
                CYCMClient.sendLocalizedMessage("script_error", e.getMessage());
                future.complete(ExecutionResult.failure(e.getMessage()));
            } catch (Exception e) {
                Constants.LOGGER.error("Unexpected CYCM script failure", e);
                CYCMClient.sendLocalizedMessage("script_runtime_error");
                future.complete(ExecutionResult.failure("Runtime error"));
            }
        });
        return future;
    }

    public record ExecutionResult(boolean succeeded, String errorMessage) {
        public static ExecutionResult successful() {
            return new ExecutionResult(true, null);
        }

        public static ExecutionResult failure(String errorMessage) {
            return new ExecutionResult(false, errorMessage);
        }
    }

    private static String preprocess(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();
        Deque<Integer> colonIndents = new ArrayDeque<>();

        for (String originalLine : lines) {
            String lineWithoutComment = stripComment(originalLine);
            if (lineWithoutComment.trim().isEmpty()) {
                continue;
            }

            int indent = countIndent(lineWithoutComment);
            while (!colonIndents.isEmpty() && indent <= colonIndents.peek()) {
                appendIndent(out, colonIndents.size() - 1);
                out.append("}\n");
                colonIndents.pop();
            }

            String trimmed = lineWithoutComment.trim();
            if (trimmed.endsWith(":")) {
                String header = trimmed.substring(0, trimmed.length() - 1).trim();
                appendIndent(out, colonIndents.size());
                out.append(header).append(" {\n");
                colonIndents.push(indent);
            } else {
                appendIndent(out, colonIndents.size());
                out.append(trimmed).append('\n');
            }
        }

        while (!colonIndents.isEmpty()) {
            appendIndent(out, colonIndents.size() - 1);
            out.append("}\n");
            colonIndents.pop();
        }

        return out.toString();
    }

    private static void appendIndent(StringBuilder out, int level) {
        for (int i = 0; i < level; i++) {
            out.append("    ");
        }
    }

    private static int countIndent(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                indent++;
            } else if (c == '\t') {
                indent += 4;
            } else {
                break;
            }
        }
        return indent;
    }

    private static String stripComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                sb.append(c);
                escaped = true;
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                sb.append(c);
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                sb.append(c);
                continue;
            }
            if (c == '#' && !inSingle && !inDouble) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static final class RuntimeContext {
        private final Map<String, Object> globals = new LinkedHashMap<>();
        private final Map<String, CommandDefinition> commands = new LinkedHashMap<>();
        private final String author;
        @SuppressWarnings("unused")
        private final String scriptName;

        private RuntimeContext(String author, String scriptName) {
            this.author = author;
            this.scriptName = scriptName;
        }

        private void setGlobal(String name, Object value) {
            globals.put(name, value);
        }

        private Object getVariable(String name, Scope scope) {
            if (scope.locals.containsKey(name)) {
                return scope.locals.get(name);
            }
            if (globals.containsKey(name)) {
                return globals.get(name);
            }
            return resolveDynamic(name);
        }

        private Object resolveDynamic(String path) {
            String normalized = path.toLowerCase(Locale.ROOT);
            if (CYCMClient.isScriptVariableBlocked(normalized)) {
                throw new ScriptException("Blocked script variable: " + path);
            }

            Minecraft client = Minecraft.getInstance();
            return switch (normalized) {
                case "server.online" -> client != null && client.player != null;
                case "server.list" -> {
                    if (client == null || client.getConnection() == null) {
                        yield List.of();
                    }
                    List<String> names = client.getConnection().getOnlinePlayers().stream()
                            .map(PlayerInfo::getProfile)
                            .filter(Objects::nonNull)
                            .map(profile -> profile.name())
                            .sorted((a, b) -> a.compareToIgnoreCase(b))
                            .collect(Collectors.toList());
                    yield names;
                }
                case "server.name" -> {
                    if (client == null) {
                        yield "Unknown";
                    }
                    ServerData currentServer = client.getCurrentServer();
                    yield currentServer != null ? currentServer.name : "Singleplayer";
                }
                case "server.ip" -> {
                    if (client == null) {
                        yield "unknown";
                    }
                    ServerData currentServer = client.getCurrentServer();
                    yield currentServer != null ? currentServer.ip : "singleplayer";
                }
                case "client.version" -> SharedConstants.getCurrentVersion().name();
                case "client.fabric_version" -> getModVersion("fabricloader");
                case "cycm.blocklist" -> CYCMClient.getBlockedCommands().stream().sorted().collect(Collectors.toList());
                case "cycm.sources" -> CYCMClient.getEnabledSources();
                case "cycm.grouping_status" -> CYCMClient.configManager.getConfig().isGroupingMessages();
                case "cycm.actionbar_status" -> CYCMClient.configManager.getConfig().isActionbarEnabled();
                case "cycm.max_repeats" -> CYCMClient.getMaxRepeats();
                case "cycm.max_delay" -> CYCMClient.getMaxDelaySeconds();
                case "cycm.max_tnt_count" -> CYCMClient.getMaxTntCount();
                case "cycm.max_tnt_radius" -> CYCMClient.getMaxTntRadius();
                case "cycm.version" -> getModVersion(Constants.MOD_ID);
                default -> throw new ScriptException("Unknown variable: " + path);
            };
        }

        private String getModVersion(String modId) {
            return FabricLoader.getInstance().getModContainer(modId)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        }

        private void defineCommand(CommandDefinition definition) {
            commands.put(definition.name, definition);
        }

        private Object invokeFunction(String name, List<Object> args, Scope scope) {
            return switch (name) {
                case "execute" -> {
                    requireArgCount(name, args, 1);
                    CYCMClient.runScriptCommand(author, stringify(args.get(0)));
                    yield null;
                }
                case "chat" -> {
                    requireArgCount(name, args, 1);
                    CYCMClient.sendScriptChatMessage(author, stringify(args.get(0)));
                    yield null;
                }
                case "range" -> {
                    requireArgCount(name, args, 2);
                    int start = toInt(args.get(0));
                    int end = toInt(args.get(1));
                    List<Integer> result = new ArrayList<>();
                    for (int i = start; i < end; i++) {
                        result.add(i);
                    }
                    yield result;
                }
                default -> invokeCommand(name, args, scope);
            };
        }

        private Object invokeCommand(String name, List<Object> args, Scope callerScope) {
            CommandDefinition definition = commands.get(name);
            if (definition == null) {
                throw new ScriptException("Unknown function/cmd: " + name);
            }
            if (definition.parameters.size() != args.size()) {
                throw new ScriptException("cmd " + name + " expects " + definition.parameters.size()
                        + " args, got " + args.size());
            }
            Scope localScope = new Scope(callerScope);
            for (int i = 0; i < definition.parameters.size(); i++) {
                localScope.locals.put(definition.parameters.get(i), args.get(i));
            }
            definition.body.execute(this, localScope);
            return null;
        }

        private Object invokeMethod(Object target, String methodName, List<Object> args) {
            if ("esc".equals(methodName)) {
                requireArgCount(methodName, args, 1);
                String text = stringify(target);
                String chars = stringify(args.get(0));
                StringBuilder escaped = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (chars.indexOf(c) >= 0 || c == '\\') {
                        escaped.append('\\');
                    }
                    escaped.append(c);
                }
                return escaped.toString();
            }
            throw new ScriptException("Unknown method: " + methodName);
        }

        private void requireArgCount(String name, List<Object> args, int expected) {
            if (args.size() != expected) {
                throw new ScriptException(name + " expects " + expected + " args, got " + args.size());
            }
        }

        private int toInt(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    throw new ScriptException("Expected int, got: " + text);
                }
            }
            throw new ScriptException("Expected int, got: " + value);
        }

        private boolean toBoolean(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            if (value instanceof String text) {
                return !text.isEmpty();
            }
            if (value instanceof List<?> list) {
                return !list.isEmpty();
            }
            return value != null;
        }

        private String stringify(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof List<?> list) {
                return list.stream().map(this::stringify).collect(Collectors.joining(", "));
            }
            return String.valueOf(value);
        }
    }

    private static final class Scope {
        private final Scope parent;
        private final Map<String, Object> locals = new LinkedHashMap<>();

        private Scope(Scope parent) {
            this.parent = parent;
        }
    }

    private interface Statement {
        void execute(RuntimeContext context, Scope scope);
    }

    private interface Expression {
        Object evaluate(RuntimeContext context, Scope scope);
    }

    private record Program(List<Statement> statements) {
        private void execute(RuntimeContext context) {
            Scope scope = new Scope(null);
            for (Statement statement : statements) {
                statement.execute(context, scope);
            }
        }
    }

    private record BlockStatement(List<Statement> statements) implements Statement {
        @Override
        public void execute(RuntimeContext context, Scope scope) {
            for (Statement statement : statements) {
                statement.execute(context, scope);
            }
        }
    }

    private record AssignmentStatement(String name, Expression expression) implements Statement {
        @Override
        public void execute(RuntimeContext context, Scope scope) {
            Object value = expression.evaluate(context, scope);
            if (scope.parent == null) {
                context.setGlobal(name, value);
            } else {
                scope.locals.put(name, value);
            }
        }
    }

    private record CommandDefinition(String name, List<String> parameters, BlockStatement body) {
    }

    private record CommandDefinitionStatement(CommandDefinition definition) implements Statement {
        @Override
        public void execute(RuntimeContext context, Scope scope) {
            context.defineCommand(definition);
        }
    }

    private record ExpressionStatement(Expression expression) implements Statement {
        @Override
        public void execute(RuntimeContext context, Scope scope) {
            expression.evaluate(context, scope);
        }
    }

    private record ForStatement(String variableName, Expression iterableExpression, BlockStatement body)
            implements Statement {
        @Override
        public void execute(RuntimeContext context, Scope scope) {
            Object iterableValue = iterableExpression.evaluate(context, scope);
            Iterable<?> iterable;
            if (iterableValue instanceof Iterable<?> it) {
                iterable = it;
            } else if (iterableValue instanceof Object[] array) {
                iterable = Arrays.asList(array);
            } else {
                throw new ScriptException("for requires iterable value");
            }
            for (Object item : iterable) {
                Scope loopScope = new Scope(scope);
                loopScope.locals.put(variableName, item);
                body.execute(context, loopScope);
            }
        }
    }

    private record IfStatement(Expression condition, BlockStatement thenBlock, BlockStatement elseBlock)
            implements Statement {
        @Override
        public void execute(RuntimeContext context, Scope scope) {
            if (context.toBoolean(condition.evaluate(context, scope))) {
                thenBlock.execute(context, new Scope(scope));
            } else if (elseBlock != null) {
                elseBlock.execute(context, new Scope(scope));
            }
        }
    }

    private record VariableExpression(String path) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            if (path.contains(".")) {
                return context.resolveDynamic(path);
            }
            Scope current = scope;
            while (current != null) {
                if (current.locals.containsKey(path)) {
                    return current.locals.get(path);
                }
                current = current.parent;
            }
            return context.getVariable(path, scope);
        }
    }

    private record LiteralExpression(Object value) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            return value;
        }
    }

    private record ListExpression(List<Expression> elements) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            List<Object> result = new ArrayList<>();
            for (Expression element : elements) {
                result.add(element.evaluate(context, scope));
            }
            return result;
        }
    }

    private record CallExpression(Expression callee, List<Expression> arguments) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            List<Object> evaluatedArgs = new ArrayList<>();
            for (Expression argument : arguments) {
                evaluatedArgs.add(argument.evaluate(context, scope));
            }
            if (callee instanceof VariableExpression variableExpression) {
                return context.invokeFunction(variableExpression.path, evaluatedArgs, scope);
            }
            if (callee instanceof PropertyAccessExpression propertyAccessExpression) {
                Object target = propertyAccessExpression.target.evaluate(context, scope);
                return context.invokeMethod(target, propertyAccessExpression.propertyName, evaluatedArgs);
            }
            throw new ScriptException("Unsupported call target");
        }
    }

    private record PropertyAccessExpression(Expression target, String propertyName) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            String fullPath = flattenPath();
            if (fullPath != null) {
                return context.resolveDynamic(fullPath);
            }

            Object value = target.evaluate(context, scope);
            if (value instanceof Map<?, ?> map) {
                return map.get(propertyName);
            }
            throw new ScriptException("Unknown property: " + propertyName);
        }

        private String flattenPath() {
            if (target instanceof VariableExpression variableExpression) {
                return variableExpression.path + "." + propertyName;
            }
            if (target instanceof PropertyAccessExpression propertyAccessExpression) {
                String prefix = propertyAccessExpression.flattenPath();
                if (prefix != null) {
                    return prefix + "." + propertyName;
                }
            }
            return null;
        }
    }

    private record FStringExpression(String template) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            Matcher matcher = INTERPOLATION_PATTERN.matcher(template);
            StringBuilder out = new StringBuilder();
            int last = 0;
            while (matcher.find()) {
                out.append(template, last, matcher.start());
                String variablePath = matcher.group(1);
                Object value = new VariableExpression(variablePath).evaluate(context, scope);
                out.append(context.stringify(value));
                last = matcher.end();
            }
            out.append(template.substring(last));
            return out.toString();
        }
    }

    private record UnaryExpression(TokenType operator, Expression expression) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            Object value = expression.evaluate(context, scope);
            return switch (operator) {
                case MINUS -> -context.toInt(value);
                case NOT -> !context.toBoolean(value);
                default -> throw new ScriptException("Unsupported unary operator: " + operator);
            };
        }
    }

    private record BinaryExpression(Expression left, TokenType operator, Expression right) implements Expression {
        @Override
        public Object evaluate(RuntimeContext context, Scope scope) {
            Object leftValue = left.evaluate(context, scope);
            Object rightValue = right.evaluate(context, scope);
            return switch (operator) {
                case PLUS -> {
                    if (leftValue instanceof Number && rightValue instanceof Number) {
                        yield context.toInt(leftValue) + context.toInt(rightValue);
                    }
                    yield context.stringify(leftValue) + context.stringify(rightValue);
                }
                case MINUS -> context.toInt(leftValue) - context.toInt(rightValue);
                case STAR -> context.toInt(leftValue) * context.toInt(rightValue);
                case SLASH -> context.toInt(leftValue) / context.toInt(rightValue);
                case EQEQ -> Objects.equals(leftValue, rightValue);
                case NEQ -> !Objects.equals(leftValue, rightValue);
                case GT -> compare(context, leftValue, rightValue) > 0;
                case GTE -> compare(context, leftValue, rightValue) >= 0;
                case LT -> compare(context, leftValue, rightValue) < 0;
                case LTE -> compare(context, leftValue, rightValue) <= 0;
                case AND -> context.toBoolean(leftValue) && context.toBoolean(rightValue);
                case OR -> context.toBoolean(leftValue) || context.toBoolean(rightValue);
                default -> throw new ScriptException("Unsupported binary operator: " + operator);
            };
        }

        private int compare(RuntimeContext context, Object leftValue, Object rightValue) {
            if (leftValue instanceof Number && rightValue instanceof Number) {
                return Integer.compare(context.toInt(leftValue), context.toInt(rightValue));
            }
            return context.stringify(leftValue).compareTo(context.stringify(rightValue));
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int index;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Program parseProgram() {
            List<Statement> statements = new ArrayList<>();
            skipNewlines();
            while (!isAtEnd()) {
                statements.add(parseStatement());
                skipNewlines();
            }
            return new Program(statements);
        }

        private Statement parseStatement() {
            if (match(TokenType.CMD)) {
                return parseCommandDefinition();
            }
            if (match(TokenType.FOR)) {
                return parseFor();
            }
            if (match(TokenType.IF)) {
                return parseIf();
            }
            if (check(TokenType.IDENTIFIER) && checkNext(TokenType.ASSIGN)) {
                String name = consume(TokenType.IDENTIFIER, "Expected variable name").lexeme;
                consume(TokenType.ASSIGN, "Expected '='");
                Expression expression = parseExpression();
                return new AssignmentStatement(name, expression);
            }
            return new ExpressionStatement(parseExpression());
        }

        private Statement parseCommandDefinition() {
            String name = consume(TokenType.IDENTIFIER, "Expected cmd name").lexeme;
            consume(TokenType.LPAREN, "Expected '(' after cmd name");
            List<String> parameters = new ArrayList<>();
            if (!check(TokenType.RPAREN)) {
                do {
                    parameters.add(consume(TokenType.IDENTIFIER, "Expected parameter name").lexeme);
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RPAREN, "Expected ')' after parameters");
            BlockStatement body = parseBlock();
            return new CommandDefinitionStatement(new CommandDefinition(name, parameters, body));
        }

        private Statement parseFor() {
            String variable = consume(TokenType.IDENTIFIER, "Expected loop variable").lexeme;
            consume(TokenType.IN, "Expected 'in' in for statement");
            Expression iterable = parseExpression();
            BlockStatement body = parseBlock();
            return new ForStatement(variable, iterable, body);
        }

        private Statement parseIf() {
            Expression condition = parseExpression();
            BlockStatement thenBlock = parseBlock();
            BlockStatement elseBlock = null;
            if (match(TokenType.ELSE)) {
                elseBlock = parseBlock();
            }
            return new IfStatement(condition, thenBlock, elseBlock);
        }

        private BlockStatement parseBlock() {
            consume(TokenType.LBRACE, "Expected '{' to start block");
            List<Statement> statements = new ArrayList<>();
            skipNewlines();
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                statements.add(parseStatement());
                skipNewlines();
            }
            consume(TokenType.RBRACE, "Expected '}' after block");
            return new BlockStatement(statements);
        }

        private Expression parseExpression() {
            return parseOr();
        }

        private Expression parseOr() {
            Expression expression = parseAnd();
            while (match(TokenType.OR)) {
                TokenType operator = previous().type;
                Expression right = parseAnd();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseAnd() {
            Expression expression = parseEquality();
            while (match(TokenType.AND)) {
                TokenType operator = previous().type;
                Expression right = parseEquality();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseEquality() {
            Expression expression = parseComparison();
            while (match(TokenType.EQEQ, TokenType.NEQ)) {
                TokenType operator = previous().type;
                Expression right = parseComparison();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseComparison() {
            Expression expression = parseTerm();
            while (match(TokenType.GT, TokenType.GTE, TokenType.LT, TokenType.LTE)) {
                TokenType operator = previous().type;
                Expression right = parseTerm();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseTerm() {
            Expression expression = parseFactor();
            while (match(TokenType.PLUS, TokenType.MINUS)) {
                TokenType operator = previous().type;
                Expression right = parseFactor();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseFactor() {
            Expression expression = parseUnary();
            while (match(TokenType.STAR, TokenType.SLASH)) {
                TokenType operator = previous().type;
                Expression right = parseUnary();
                expression = new BinaryExpression(expression, operator, right);
            }
            return expression;
        }

        private Expression parseUnary() {
            if (match(TokenType.MINUS, TokenType.NOT)) {
                TokenType operator = previous().type;
                Expression right = parseUnary();
                return new UnaryExpression(operator, right);
            }
            return parseCall();
        }

        private Expression parseCall() {
            Expression expression = parsePrimary();
            while (true) {
                if (match(TokenType.LPAREN)) {
                    List<Expression> arguments = new ArrayList<>();
                    if (!check(TokenType.RPAREN)) {
                        do {
                            arguments.add(parseExpression());
                        } while (match(TokenType.COMMA));
                    }
                    consume(TokenType.RPAREN, "Expected ')' after args");
                    expression = new CallExpression(expression, arguments);
                } else if (match(TokenType.DOT)) {
                    String property = consume(TokenType.IDENTIFIER, "Expected property name").lexeme;
                    expression = new PropertyAccessExpression(expression, property);
                } else {
                    break;
                }
            }
            return expression;
        }

        private Expression parsePrimary() {
            if (match(TokenType.NUMBER)) {
                return new LiteralExpression(Integer.parseInt(previous().lexeme));
            }
            if (match(TokenType.STRING)) {
                return new LiteralExpression(previous().literal);
            }
            if (match(TokenType.FSTRING)) {
                return new FStringExpression((String) previous().literal);
            }
            if (match(TokenType.TRUE)) {
                return new LiteralExpression(true);
            }
            if (match(TokenType.FALSE)) {
                return new LiteralExpression(false);
            }
            if (match(TokenType.LBRACKET)) {
                List<Expression> elements = new ArrayList<>();
                if (!check(TokenType.RBRACKET)) {
                    do {
                        elements.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RBRACKET, "Expected ']'");
                return new ListExpression(elements);
            }
            if (match(TokenType.IDENTIFIER)) {
                return new VariableExpression(previous().lexeme);
            }
            if (match(TokenType.LPAREN)) {
                Expression expression = parseExpression();
                consume(TokenType.RPAREN, "Expected ')'");
                return expression;
            }
            throw error(peek(), "Unexpected token: " + peek().lexeme);
        }

        private void skipNewlines() {
            while (match(TokenType.NEWLINE)) {
                // skip
            }
        }

        private boolean match(TokenType... types) {
            for (TokenType type : types) {
                if (check(type)) {
                    advance();
                    return true;
                }
            }
            return false;
        }

        private Token consume(TokenType type, String message) {
            if (check(type)) {
                return advance();
            }
            throw error(peek(), message);
        }

        private boolean check(TokenType type) {
            if (isAtEnd()) {
                return type == TokenType.EOF;
            }
            return peek().type == type;
        }

        private boolean checkNext(TokenType type) {
            if (index + 1 >= tokens.size()) {
                return false;
            }
            return tokens.get(index + 1).type == type;
        }

        private Token advance() {
            if (!isAtEnd()) {
                index++;
            }
            return previous();
        }

        private boolean isAtEnd() {
            return peek().type == TokenType.EOF;
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token previous() {
            return tokens.get(index - 1);
        }

        private ScriptException error(Token token, String message) {
            return new ScriptException(message + " at token '" + token.lexeme + "'");
        }
    }

    private enum TokenType {
        IDENTIFIER,
        NUMBER,
        STRING,
        FSTRING,
        TRUE,
        FALSE,
        CMD,
        FOR,
        IN,
        IF,
        ELSE,
        AND,
        OR,
        NOT,
        ASSIGN,
        EQEQ,
        NEQ,
        GT,
        GTE,
        LT,
        LTE,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        DOT,
        COMMA,
        LPAREN,
        RPAREN,
        LBRACE,
        RBRACE,
        LBRACKET,
        RBRACKET,
        NEWLINE,
        EOF
    }

    private record Token(TokenType type, String lexeme, Object literal) {
    }

    private static final class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int current;

        private Lexer(String source) {
            this.source = source;
        }

        private List<Token> tokenize() {
            while (!isAtEnd()) {
                scanToken();
            }
            tokens.add(new Token(TokenType.EOF, "", null));
            return tokens;
        }

        private void scanToken() {
            char c = advance();
            switch (c) {
                case ' ', '\t' -> {
                }
                case '\n' -> tokens.add(new Token(TokenType.NEWLINE, "\\n", null));
                case '(' -> tokens.add(new Token(TokenType.LPAREN, "(", null));
                case ')' -> tokens.add(new Token(TokenType.RPAREN, ")", null));
                case '{' -> tokens.add(new Token(TokenType.LBRACE, "{", null));
                case '}' -> tokens.add(new Token(TokenType.RBRACE, "}", null));
                case '[' -> tokens.add(new Token(TokenType.LBRACKET, "[", null));
                case ']' -> tokens.add(new Token(TokenType.RBRACKET, "]", null));
                case ',' -> tokens.add(new Token(TokenType.COMMA, ",", null));
                case '.' -> tokens.add(new Token(TokenType.DOT, ".", null));
                case '+' -> tokens.add(new Token(TokenType.PLUS, "+", null));
                case '-' -> tokens.add(new Token(TokenType.MINUS, "-", null));
                case '*' -> tokens.add(new Token(TokenType.STAR, "*", null));
                case '/' -> tokens.add(new Token(TokenType.SLASH, "/", null));
                case '=' -> {
                    boolean eq = match('=');
                    tokens.add(new Token(eq ? TokenType.EQEQ : TokenType.ASSIGN, eq ? "==" : "=", null));
                }
                case '!' -> {
                    if (match('=')) {
                        tokens.add(new Token(TokenType.NEQ, "!=", null));
                    } else {
                        throw new ScriptException("Unexpected '!'");
                    }
                }
                case '>' -> {
                    boolean eq = match('=');
                    tokens.add(new Token(eq ? TokenType.GTE : TokenType.GT, eq ? ">=" : ">", null));
                }
                case '<' -> {
                    boolean eq = match('=');
                    tokens.add(new Token(eq ? TokenType.LTE : TokenType.LT, eq ? "<=" : "<", null));
                }
                case '\'', '"' -> string(c, false);
                case 'f' -> {
                    if (peek() == '"' || peek() == '\'') {
                        char quote = advance();
                        string(quote, true);
                    } else {
                        identifier();
                    }
                }
                default -> {
                    if (Character.isDigit(c)) {
                        number();
                    } else if (isIdentifierStart(c)) {
                        identifier();
                    } else {
                        throw new ScriptException("Unexpected character: " + c);
                    }
                }
            }
        }

        private void identifier() {
            int start = current - 1;
            while (!isAtEnd() && isIdentifierPart(peek())) {
                advance();
            }
            String text = source.substring(start, current);
            TokenType type = switch (text) {
                case "cmd" -> TokenType.CMD;
                case "for" -> TokenType.FOR;
                case "in" -> TokenType.IN;
                case "if" -> TokenType.IF;
                case "else" -> TokenType.ELSE;
                case "and" -> TokenType.AND;
                case "or" -> TokenType.OR;
                case "not" -> TokenType.NOT;
                case "true" -> TokenType.TRUE;
                case "false" -> TokenType.FALSE;
                default -> TokenType.IDENTIFIER;
            };
            tokens.add(new Token(type, text, null));
        }

        private void number() {
            int start = current - 1;
            while (!isAtEnd() && Character.isDigit(peek())) {
                advance();
            }
            tokens.add(new Token(TokenType.NUMBER, source.substring(start, current), null));
        }

        private void string(char quote, boolean fString) {
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            while (!isAtEnd()) {
                char c = advance();
                if (escaped) {
                    value.append(switch (c) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        case '\\' -> '\\';
                        case '\'' -> '\'';
                        case '"' -> '"';
                        default -> c;
                    });
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == quote) {
                    tokens.add(new Token(fString ? TokenType.FSTRING : TokenType.STRING, value.toString(),
                            value.toString()));
                    return;
                }
                value.append(c);
            }
            throw new ScriptException("Unterminated string literal");
        }

        private boolean match(char expected) {
            if (isAtEnd() || source.charAt(current) != expected) {
                return false;
            }
            current++;
            return true;
        }

        private char advance() {
            return source.charAt(current++);
        }

        private boolean isAtEnd() {
            return current >= source.length();
        }

        private char peek() {
            if (isAtEnd()) {
                return '\0';
            }
            return source.charAt(current);
        }

        private boolean isIdentifierStart(char c) {
            return Character.isLetter(c) || c == '_';
        }

        private boolean isIdentifierPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }
    }

    private static final class ScriptException extends RuntimeException {
        private ScriptException(String message) {
            super(message);
        }
    }
}
