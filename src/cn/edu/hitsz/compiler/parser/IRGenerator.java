package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// TODO: 实验三: 实现 IR 生成

/**
 *
 */
public class IRGenerator implements ActionObserver {
    private final Stack<IRValue> stack = new Stack<>();
    private final List<Instruction> ir = new ArrayList<>();
    private SymbolTable symbolTable = null;

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        if(currentToken.getKind() == TokenKind.fromString("id")) {
            stack.push(IRVariable.named(currentToken.getText()));
        } else if (currentToken.getKind() == TokenKind.fromString("IntConst")) {
            stack.push(IRImmediate.of(Integer.parseInt(currentToken.getText())));
        } else {
            stack.push(null);
        }
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        switch (production.index()) {
            case 6 -> { // S -> id = F
                var f = stack.pop();
                var eq = stack.pop();
                var id = stack.pop();
                assert f != null && eq == null;
                assert id instanceof IRVariable;

                var instruction = Instruction.createMov((IRVariable) id, f);
                ir.add(instruction);
                stack.push(null);
            }
            case 7 -> { // S -> return F
                var f = stack.pop();
                var ret = stack.pop();
                assert f != null && ret == null;

                var instruction = Instruction.createRet(f);
                ir.add(instruction);
                stack.push(null);
            }
            case 8 -> { // F -> G ? E : E
                var e2 = stack.pop();
                var colon = stack.pop();
                var e1 = stack.pop();
                var question = stack.pop();
                var g = stack.pop();
                assert g != null && e1 != null && e2 != null && colon == null && question == null;

                var result = IRVariable.temp();
                var instruction1 = Instruction.createMov(result, e2);
                var instruction2 = Instruction.createCmov(result, g, e1);
                ir.add(instruction1);
                ir.add(instruction2);
                stack.push(result);
            }
            case 9, 12, 15, 17, 19, 20 -> { // F -> E
                // do nothing
            }
            case 10, 11, 13, 14, 16 -> { // X -> X @ Y
                var y = stack.pop();
                var o = stack.pop();
                var x = stack.pop();
                assert x != null && y != null && o == null;

                var result = IRVariable.temp();

                var op = production.body().get(1);
                assert op instanceof TokenKind;
                var instruction = switch (op.getTermName()) {
                    case "+" -> Instruction.createAdd(result, x, y);
                    case "-" -> Instruction.createSub(result, x, y);
                    case "*" -> Instruction.createMul(result, x, y);
                    case ">" -> Instruction.createGt(result, x, y);
                    case "<" -> Instruction.createLt(result, x, y);
                    default -> throw new IllegalStateException("Unexpected production: " + production);
                };

                ir.add(instruction);
                stack.push(result);
            }
            case 18 -> { // B -> ( F )
                var p2 = stack.pop();
                var f = stack.pop();
                var p1 = stack.pop();
                assert p1 == null && p2 == null && f != null;

                stack.push(f);
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
    public void whenAccept(Status currentStatus) {
        assert stack.size() == 1;
        assert stack.pop() == null;
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }

    public List<Instruction> getIR() {
        return ir;
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }
}

