package cn.edu.hitsz.compiler.asm;

import java.util.List;

public class RVInstruction {
    private final String op;
    private final List<String> operands;

    public RVInstruction(String op, List<String> operands) {
        this.op = op;
        this.operands = operands;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(op);
        for (var operand : operands) {
            sb.append(" ").append(operand);
        }
        return sb.toString();
    }
}
