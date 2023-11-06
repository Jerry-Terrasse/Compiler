package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.*;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.swap;


/**
 * TODO: 实验四: 实现汇编生成
 * <br>
 * 在编译器的整体框架中, 代码生成可以称作后端, 而前面的所有工作都可称为前端.
 * <br>
 * 在前端完成的所有工作中, 都是与目标平台无关的, 而后端的工作为将前端生成的目标平台无关信息
 * 根据目标平台生成汇编代码. 前后端的分离有利于实现编译器面向不同平台生成汇编代码. 由于前后
 * 端分离的原因, 有可能前端生成的中间代码并不符合目标平台的汇编代码特点. 具体到本项目你可以
 * 尝试加入一个方法将中间代码调整为更接近 risc-v 汇编的形式, 这样会有利于汇编代码的生成.
 * <br>
 * 为保证实现上的自由, 框架中并未对后端提供基建, 在具体实现时可自行设计相关数据结构.
 *
 * @see AssemblyGenerator#run() 代码生成与寄存器分配
 */
public class AssemblyGenerator {

    private List<Instruction> instructions = null;
    private final HashMap<IRVariable, Integer> lastUse = new HashMap<>();
    private final HashMap<Integer, List<IRVariable>> timeToUnbind = new HashMap<>();
    private final List<RVInstruction> rvInsts = new LinkedList<>();

    /**
     * 加载前端提供的中间代码
     * <br>
     * 视具体实现而定, 在加载中或加载后会生成一些在代码生成中会用到的信息. 如变量的引用
     * 信息. 这些信息可以通过简单的映射维护, 或者自行增加记录信息的数据结构.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        instructions = originInstructions;
        for(int i = 0; i < instructions.size(); i++) {
            if(instructions.get(i).getKind().isReturn()) {
                continue;
            }
            updateLastUse(instructions.get(i).getResult(), i);
        }
        for(int i = 0; i < instructions.size(); i++) {
            var inst = instructions.get(i);
            for(IRValue var : inst.getOperands()) {
                if(var.isIRVariable()) {
                    updateLastUse((IRVariable) var, i);
                }
            }
            if(inst.getKind() == InstructionKind.GT) {
                instructions.set(i, Instruction.createLt(
                    inst.getResult(),
                    inst.getOperands().get(1),
                    inst.getOperands().get(0)
                ));
            }
        }

        for(IRVariable var : lastUse.keySet()) {
            if(!timeToUnbind.containsKey(lastUse.get(var))) {
                timeToUnbind.put(lastUse.get(var), new LinkedList<>());
            }
            timeToUnbind.get(lastUse.get(var)).add(var);
        }
    }

    private void updateLastUse(IRVariable var, int index) {
        if (lastUse.containsKey(var)) {
            lastUse.put(var, Math.max(lastUse.get(var), index));
        } else {
            lastUse.put(var, index);
        }
    }


    /**
     * 执行代码生成.
     * <br>
     * 根据理论课的做法, 在代码生成时同时完成寄存器分配的工作. 若你觉得这样的做法不好,
     * 也可以将寄存器分配和代码生成分开进行.
     * <br>
     * 提示: 寄存器分配中需要的信息较多, 关于全局的与代码生成过程无关的信息建议在代码生
     * 成前完成建立, 与代码生成的过程相关的信息可自行设计数据结构进行记录并动态维护.
     */
    public void run() {
        for(int i = 0; i < instructions.size(); ++i) {
            var inst = instructions.get(i);
            var kind = inst.getKind();
            if(kind.isUnary()) {
                // only MOV
                var src = inst.getOperands().get(0);
                var dst = Reg.getReg(inst.getResult());
                if (src.isImmediate()) {
                    rvInsts.add(new RVInstruction("li", List.of(
                            dst.toString(),
                            src.toString()
                    )));
                } else {
                    rvInsts.add(new RVInstruction("addi", List.of(
                            dst.toString(),
                            Reg.getReg((IRVariable) src).toString(),
                            "0"
                    )));
                }
            } else if(kind.isReturn()) {
                var src = inst.getOperands().get(0);
                if(src.isImmediate()) {
                    rvInsts.add(new RVInstruction("li", List.of(
                        "x10",
                        src.toString()
                    )));
                } else {
                    rvInsts.add(new RVInstruction("addi", List.of(
                        "x10",
                        Reg.getReg((IRVariable) src).toString(),
                        "0"
                    )));
                }
                return;
            } else if(kind == InstructionKind.ADD && (inst.getLHS().isImmediate() != inst.getRHS().isImmediate())) {
                // ADD can be optimized to ADDI
                var dst = Reg.getReg(inst.getResult());
                var op1 = inst.getOperands().get(0);
                var op2 = inst.getOperands().get(1);
                if(op1.isImmediate()) {
                    op1 = inst.getOperands().get(1);
                    op2 = inst.getOperands().get(0);
                }
                // now op1 is variable, op2 is immediate
                rvInsts.add(new RVInstruction("addi", List.of(
                        dst.toString(),
                        Reg.getReg((IRVariable) op1).toString(),
                        op2.toString()
                )));
            } else {
                // SUB GT LT CMOV MUL ADD
                var dst = Reg.getReg(inst.getResult());
                var op1 = inst.getOperands().get(0);
                var op2 = inst.getOperands().get(1);
                if(op1.isImmediate() && op2.isImmediate()) {
                    var val1 = Integer.parseInt(op1.toString());
                    var val2 = Integer.parseInt(op2.toString());
                    Integer result = switch (kind) {
                        case ADD -> val1 + val2;
                        case SUB -> val1 - val2;
                        case LT -> val1 < val2 ? 1 : 0;
                        case MUL -> val1 * val2;
                        case CMOV -> val1 == 0 ? null : val2;
                        default -> throw new IllegalStateException("Unexpected value: " + kind);
                    };
                    if(result != null) {
                        rvInsts.add(new RVInstruction("li", List.of(
                                dst.toString(),
                                String.valueOf(result)
                        )));
                    }
                } else {
                    IRVariable op1Tmp = null, op2Tmp = null;
                    if(op1.isImmediate()) {
                        op1Tmp = IRVariable.temp();
                        rvInsts.add(new RVInstruction("li", List.of(
                            Reg.getReg(op1Tmp).toString(),
                            op1.toString()
                        )));
                    } else {
                        op1Tmp = (IRVariable) op1;
                    }
                    if(op2.isImmediate()) {
                        op2Tmp = IRVariable.temp();
                        rvInsts.add(new RVInstruction("li", List.of(
                            Reg.getReg(op2Tmp).toString(),
                            op2.toString()
                        )));
                    } else {
                        op2Tmp = (IRVariable) op2;
                    }
                    if(kind == InstructionKind.CMOV) {
                        rvInsts.add(new RVInstruction("beq", List.of(
                            Reg.getReg(op1Tmp).toString(),
                            "x0",
                            "L" + i
                        )));
                        rvInsts.add(new RVInstruction("addi", List.of(
                            dst.toString(),
                            Reg.getReg(op2Tmp).toString(),
                            "0"
                        )));
                        rvInsts.add(new RVInstruction("L" + i + ":", new ArrayList<>()));
                    } else {
                        String op = switch (kind) {
                            case ADD -> "add";
                            case SUB -> "sub";
                            case LT -> "slt";
                            case MUL -> "mul";
                            default -> throw new IllegalStateException("Unexpected value: " + kind);
                        };
                        rvInsts.add(new RVInstruction(op, List.of(
                                dst.toString(),
                                Reg.getReg(op1Tmp).toString(),
                                Reg.getReg(op2Tmp).toString()
                        )));
                    }
                    if(op1.isImmediate()) {
                        Reg.freeReg(op1Tmp);
                    }
                    if(op2.isImmediate()) {
                        Reg.freeReg(op2Tmp);
                    }
                }
            }
            // free register
            if (timeToUnbind.get(i) == null) {
                continue;
            }
            for(IRVariable var: timeToUnbind.get(i)) {
                Reg.freeReg(var);
            }
        }
    }


    /**
     * 输出汇编代码到文件
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeLines(
            path,
            rvInsts.stream().map(RVInstruction::toString).toList()
        );
    }
}

