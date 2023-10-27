package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.parser.table.Term;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

// TODO: 实验三: 实现语义分析
public class SemanticAnalyzer implements ActionObserver {
    private final Stack<Token> stack = new Stack<>();
    private SymbolTable symbolTable = null;

    @Override
    public void whenAccept(Status currentStatus) {
        assert stack.size() == 1;
        assert stack.pop() == null;
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        switch (production.index()) {
            case 5 -> { // D -> int
                var kw = stack.pop();
                assert kw.getKind() == TokenKind.fromString("int");
                stack.push(kw);
            }
            case 4 -> { // S -> D id
                var id = stack.pop();
                var d = stack.pop();
                assert id.getKind() == TokenKind.fromString("id");
                assert d.getKind() == TokenKind.fromString("int");
                var entry = symbolTable.get(id.getText());
                entry.setType(SourceCodeType.Int);

                stack.push(null);
            }
            default -> {
                for(var rhs: production.body()) {
                    stack.pop();
                }
                stack.push(null);
            }
        }
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        stack.push(currentToken);
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }
}

