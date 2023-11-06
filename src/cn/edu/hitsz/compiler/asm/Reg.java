package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRVariable;

import java.util.HashMap;

public class Reg {
    private final int index;
    private static final HashMap<Reg, IRVariable> reg2var = new HashMap<>();
    private static final HashMap<IRVariable, Reg> var2reg = new HashMap<>();
    private Reg(int index) {
        assert index == 10 || (5 <= index && index <= 7) || (28 <= index && index <= 31);
        this.index = index;
    }

    private static final Reg[] availableRegs = {
            new Reg(5),
            new Reg(6),
            new Reg(7),
            new Reg(28),
            new Reg(29),
            new Reg(30),
            new Reg(31)
    };

    private static void bind(Reg reg, IRVariable var) {
        assert !reg2var.containsKey(reg);
        assert !var2reg.containsKey(var);
        reg2var.put(reg, var);
        var2reg.put(var, reg);
    }
    private static void unbind(Reg reg, IRVariable var) {
        reg2var.remove(reg);
        var2reg.remove(var);
    }

    public static Reg getReg(IRVariable var) {
        if (var2reg.containsKey(var)) {
            return var2reg.get(var);
        }
        for (Reg reg : availableRegs) {
            if (!reg2var.containsKey(reg)) {
                bind(reg, var);
                return reg;
            }
        }
        throw new RuntimeException("No available register");
    }
    public static void freeReg(IRVariable var) {
        assert var2reg.containsKey(var);
        unbind(var2reg.get(var), var);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Reg) {
            return ((Reg) obj).index == index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public String toString() {
        return "x" + index;
    }
}
