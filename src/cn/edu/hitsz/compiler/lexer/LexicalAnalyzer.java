package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private final Queue<Character> buffer = new ArrayDeque<>();
    private final List<Token> result = new LinkedList<>();

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * 从给予的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        // 词法分析前的缓冲区实现
        // 可自由实现各类缓冲区
        // 或直接采用完整读入方法
        var content = FileUtils.readFile(path);
        for (var c : content.toCharArray()) {
            buffer.add(c);
        }
        buffer.add('$');
    }

    enum State {
        INIT,
        SINGLE_CHAR_KW, // =, `,`, ;, +, -, *, /, (, ), ?, :, >, <
        KW_INT, // int, maybe -> ID
        KW_RETURN, // return, maybe -> ID
        ID, // 标识符[a-zA-Z_][a-zA-Z]*
        INT_CONST, // 整数常量[0-9]+
    }
    Map<State, String> kwStr = new HashMap<>() {{
        put(State.KW_INT, "int");
        put(State.KW_RETURN, "return");
    }};
    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        // 自动机实现的词法分析过程
        State state = State.INIT;
        StringBuilder text = new StringBuilder();
        char cur = 0;
        boolean nxt = true;
        while (true) {
            if (nxt) {
                assert !buffer.isEmpty();
                cur = buffer.poll();
            }
            nxt = true;
            text.append(cur);
            if (state == State.INIT && cur == '$') {
                // finish
                result.add(Token.eof());
                break;
            }
            switch (state) {
                case INIT -> {
                    switch (cur) {
                        case ' ', '\t', '\n', '\r' -> text.deleteCharAt(text.length()-1);
                        case '=', ',', ';', '+', '-', '*', '/', '(', ')', '?', ':', '>', '<' -> state = State.SINGLE_CHAR_KW;
                        case 'i' -> state = State.KW_INT;
                        case 'r' -> state = State.KW_RETURN;
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> state = State.INT_CONST;
                        default -> {
                            if (Character.isLetter(cur)) {
                                state = State.ID;
                            } else {
                                throw new RuntimeException("Unexpected character: " + cur);
                            }
                        }
                    }
                }
                case KW_INT, KW_RETURN -> {
                    String kw = kwStr.get(state);
                    if (text.length() > kw.length()) {
                        if(Character.isLetter(cur)) {
                            // mismatch
                            text.deleteCharAt(text.length()-1);
                            nxt = false; state = State.ID;
                        } else {
                            // commit
                            text.deleteCharAt(text.length()-1);
                            nxt = false; state = State.INIT;
                            result.add(Token.normal(TokenKind.fromString(kw), text.toString()));
                            text.delete(0, text.length());
                        }
                    } else if (kw.charAt(text.length()-1) != text.charAt(text.length()-1)) {
                        // mismatch
                        text.deleteCharAt(text.length()-1);
                        nxt = false; state = State.ID;
                    }
                    // continue matching
                }
                case INT_CONST -> {
                    if (!Character.isDigit(cur)) {
                        // commit
                        text.deleteCharAt(text.length()-1);
                        nxt = false; state = State.INIT;
                        result.add(Token.normal(TokenKind.fromString("IntConst"), text.toString()));
                        text.delete(0, text.length());
                    }
                    // continue matching
                }
                case ID -> {
                    if (!Character.isLetter(cur)) {
                        // commit
                        text.deleteCharAt(text.length()-1);
                        nxt = false; state = State.INIT;
                        result.add(Token.normal(TokenKind.fromString("id"), text.toString()));
                        symbolTable.findOrAdd(text.toString());
                        text.delete(0, text.length());
                    }
                    // continue matching
                }
                case SINGLE_CHAR_KW -> {
                    // commit
                    text.deleteCharAt(text.length()-1);
                    nxt = false; state = State.INIT;
                    result.add(Token.simple(TokenKind.fromString(text.toString())));
                    text.delete(0, text.length());
                }
                default -> throw new RuntimeException("Unexpected state: " + state);
            }
        }
    }


    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        // 从词法分析过程中获取 Token 列表
        // 词法分析过程可以使用 Stream 或 Iterator 实现按需分析
        // 亦可以直接分析完整个文件
        // 总之实现过程能转化为一列表即可
        return result;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }
}