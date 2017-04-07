package comp207p.main;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;


public class ConstantFolder {
    ClassParser parser = null;
    ClassGen gen = null;

    JavaClass original = null;
    JavaClass optimized = null;

    Stack<Number> constantStack = null;
    HashMap<Integer, Number> vars = null;

    public ConstantFolder(String classFilePath)
    {
        try {
            System.out.println(classFilePath);
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * SAFE DELETE INSTRUCTIONS
     * @param handle
     * @param list
     *
     * This method takes an instruction handle that is found within an instruction list and safely deletes it from the
     * list by remove all references to it from any GOTO instructions in the list.
     */
    private void deleteInstruction(InstructionHandle handle, InstructionList list)
    {
        try {
            list.delete(handle);
        } catch (TargetLostException e) {
            InstructionHandle[] targets = e.getTargets();

            for (InstructionHandle target : targets) {
                InstructionTargeter[] targeters = target.getTargeters();
                for (InstructionTargeter targeter : targeters)
                    targeter.updateTarget(target, null);
            }
        }
    }

    /**
     * PERFORM LOGIC
     * Evaluate logical expressions. Given a logical comparison instruction, this method will evaluate it based on
     * the two parameters from the top of the stack and return the result of the comparison
     * @param handle the handle of the logic
     * @return result
     */
    private boolean performLogOp(InstructionHandle handle)
    {
        Instruction inst = handle.getInstruction();
        if (inst instanceof IFLE) {
            Number value1 = constantStack.pop();
            return (Integer) value1 <= 0;
        }

        Number x = constantStack.pop();
        Number y = constantStack.pop();

        // identify what kind of operation it is, and then perform the op.
        boolean result = false;
        if (inst instanceof IF_ICMPEQ) {
            if (x.equals(y)) {
                result = true;
            }
        } else if (inst instanceof IF_ICMPGE) {
            if ((Integer) x >= (Integer) y) {
                result = true;
            }
        } else if (inst instanceof IF_ICMPGT) {
            if ((Integer) x > (Integer) y) {
                result = true;
            }
        } else if (inst instanceof IF_ICMPLE) {
            if ((Integer) x <= (Integer) y) {
                result = true;
            }
        } else if (inst instanceof IF_ICMPLT) {
            if ((Integer) x < (Integer) y) {
                result = true;
            }
        } else if (inst instanceof IF_ICMPNE) {
            if (!x.equals(y)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * PERFORM ARITHMETIC
     * Given an arithmetic instruction, this method will take the two operands from the top of the stack and evaluate
     * the expression. It will place the result onto the stack to replace the operands.
     * @param handle the handle of the arithmetic instruction
     */
    private void performArithOp(InstructionHandle handle)
    {
        Number x = constantStack.pop();
        Number y = constantStack.pop();

        // identify what kind of operation it is, and then perform the op.
        if (handle.getInstruction() instanceof IADD) {
            Number newValue = y.intValue() + x.intValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof LADD) {
            Number newValue = y.longValue() + x.longValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof FADD) {
            Number newValue = y.floatValue() + x.floatValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof DADD) {
            Number newValue = y.doubleValue() + x.doubleValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof IMUL) {
            Number newValue = y.intValue() * x.intValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof LMUL) {
            Number newValue = y.longValue() * x.longValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof FMUL) {
            Number newValue = y.floatValue() * x.floatValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof DMUL) {
            Number newValue = y.doubleValue() * x.doubleValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof ISUB) {
            Number newValue = y.intValue() - x.intValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof LSUB) {
            Number newValue = y.longValue() - x.longValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof FSUB) {
            Number newValue = y.floatValue() - x.floatValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof DSUB) {
            Number newValue = y.doubleValue() - x.doubleValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof IDIV) {
            Number newValue = y.intValue() / x.intValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof LDIV) {
            Number newValue = y.longValue() / x.longValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof FDIV) {
            Number newValue = y.floatValue() / x.floatValue();
            constantStack.push(newValue);
        } else if (handle.getInstruction() instanceof DDIV) {
            Number newValue = y.doubleValue() / x.doubleValue();
            constantStack.push(newValue);
        }
    }

    private void removeLDCs(InstructionHandle handle, InstructionList instList, int toDelete)
    {
        int times = 0;
        InstructionHandle handleToCheck = handle.getPrev();
        while (times != toDelete) {

            if ((handleToCheck.getInstruction() instanceof LDC) || (handleToCheck.getInstruction() instanceof LDC2_W)) {
                times++;
                if (times < toDelete) {
                    handleToCheck = handleToCheck.getPrev();
                    deleteInstruction(handleToCheck.getNext(), instList);
                    continue;
                } else {
                    deleteInstruction(handleToCheck, instList);
                }

            } else if (handleToCheck.getPrev() == null) {
                break;
            }
            handleToCheck = handleToCheck.getPrev();
        }
    }

    /**
     * This method goes through list of instructions and identifies the index of start and end locations of for loops,
     * as well as the index of the loop variable. These three numbers are grouped and stored in a list to represent the
     * loop
     * @param instList
     * @return loopIndexArray (len = 3 * number of loops in instList)
     */
    private ArrayList<Integer> findLoops(InstructionList instList)
    {
        ArrayList<Integer> loopPositions = new ArrayList<>();

        for (InstructionHandle handle : instList.getInstructionHandles()) {
            Instruction inst = handle.getInstruction();

            if (inst instanceof IINC) {
                InstructionHandle nextInstructionHandle = handle.getNext();
                Instruction nextInstruction = nextInstructionHandle.getInstruction();
                Integer index = ((IINC) inst).getIndex();
                if (nextInstruction instanceof GotoInstruction) {
                    InstructionHandle targetHandle = ((GotoInstruction) nextInstruction).getTarget();
                    Integer start = targetHandle.getPosition() - 2;
                    loopPositions.add(start);
                    loopPositions.add(nextInstructionHandle.getPosition());
                    loopPositions.add(index);
                }
            }

        }
        return loopPositions;
    }

    private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method)
    {
        Code methodCode = method.getCode();
        constantStack = new Stack<Number>();
        vars = new HashMap<Integer, Number>();

        InstructionList instList = new InstructionList(methodCode.getCode());

        System.out.println("\n*****************************************");
        System.out.println("Optimising method: " + method.getName()+ " ("+original.getClassName()+")");
        System.out.println("*****************************************");
        System.out.println("\nPROCESSING:");

        //Create a method generator from original method
        MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), instList, cpgen);
        boolean skipNextArith = false;

        boolean justDeletedIf = false;

        int constants = 0;
        ArrayList<Integer> arrayForLoops = findLoops(instList);
        for (InstructionHandle handle : instList.getInstructionHandles()) {
            boolean inLoop = false;
            for (int i = 0; i < arrayForLoops.size(); i += 3) {
                Integer one = arrayForLoops.get(i);
                Integer two = arrayForLoops.get(i + 1);
                if (handle.getPosition() <= two && handle.getPosition() >= one) {
                    inLoop = true; //Checks whether in the loop
                }
            }
            if (handle.getInstruction() == null) {
                continue;
            }
            System.out.println(handle + "\tSTACK:" + constantStack);

            boolean isLDC = (handle.getInstruction() instanceof LDC) || (handle.getInstruction() instanceof LDC_W) || (handle.getInstruction() instanceof LDC2_W);
            boolean isArithmeticInst = (handle.getInstruction() instanceof ArithmeticInstruction);
            boolean isPush = (handle.getInstruction() instanceof SIPUSH) || (handle.getInstruction() instanceof BIPUSH);
            boolean isConst = (handle.getInstruction() instanceof ICONST || handle.getInstruction() instanceof FCONST || handle.getInstruction() instanceof LCONST || handle.getInstruction() instanceof DCONST);
            boolean isStore = (handle.getInstruction() instanceof StoreInstruction);
            boolean isLoad = (handle.getInstruction() instanceof LoadInstruction);
            boolean isComparison = (handle.getInstruction() instanceof IfInstruction);
            boolean isLongComparison = (handle.getInstruction() instanceof LCMP);
            boolean isGoto = (handle.getInstruction() instanceof GotoInstruction);
            boolean isConversion = (handle.getInstruction() instanceof I2D);

            if (!isArithmeticInst && !isStore) {
                skipNextArith = false; //Makes sure the code doesn't skip an incorrect instruction.
            }
            if (inLoop) {
                if (isLoad) {
                    boolean removeLoad = true;
                    for (int i = 0; i < arrayForLoops.size(); i += 3) {
                        Integer index = arrayForLoops.get(i + 2);
                        int nowIndex = ((LoadInstruction) handle.getInstruction()).getIndex();
                        /* We are seeing if this load instruction is the load instruction for the loop variable
                           If it is, then we should not remove this load instruction
                         */

                        if (index == nowIndex) {
                            removeLoad = false;
                            skipNextArith = true;
                            /*
                            avoid optimising arithmetic instructions where the loop variable is involved
                            */
                        }
                    }
                    if (removeLoad) {
                        if (!(handle.getInstruction() instanceof ALOAD)) {
                            int index = ((LoadInstruction) handle.getInstruction()).getIndex();
                            Number topOfStack = vars.get(index);

                            constantStack.push(topOfStack);
                            if (topOfStack instanceof Double) {
                                instList.insert(handle, new LDC2_W(cpgen.addDouble((Double) topOfStack)));
                            } else if (topOfStack instanceof Long) {
                                instList.insert(handle, new LDC2_W(cpgen.addLong((Long) topOfStack)));
                            } else if (topOfStack instanceof Integer) {
                                handle.setInstruction(new LDC(cpgen.addInteger((Integer) topOfStack)));
                            } else if (topOfStack instanceof Float) {
                                instList.insert(handle, new LDC(cpgen.addFloat((Float) topOfStack)));
                            }

                        }
                    }
                }
                if (isArithmeticInst && (!skipNextArith)) {
                    if (constants >= 2) {
                        removeLDCs(handle, instList, 1);
                    } else {
                        removeLDCs(handle, instList, 0);
                    }
                    performArithOp(handle);
                    Number topOfStack = constantStack.pop();
                    constants++;
                    if (topOfStack instanceof Double) {
                        instList.insert(handle, new LDC2_W(cpgen.addDouble((Double) topOfStack)));
                    } else if (topOfStack instanceof Long) {
                        instList.insert(handle, new LDC2_W(cpgen.addLong((Long) topOfStack)));
                    } else if (topOfStack instanceof Integer) {
                        instList.insert(handle, new LDC(cpgen.addInteger((Integer) topOfStack)));
                    } else if (topOfStack instanceof Float) {
                        instList.insert(handle, new LDC(cpgen.addFloat((Float) topOfStack)));
                    }

                    constantStack.push(topOfStack);
                    deleteInstruction(handle, instList);
                }
                if (isArithmeticInst && (skipNextArith)) {
                    skipNextArith = false;
                }
                continue;
            }

            //x = 5+4
            //LDC 5
            //LDC 4
            //IADD

            if (isLDC || isPush) {
                Number value = getConstantValue(handle, cpgen);
                constantStack.push(value);
                deleteInstruction(handle, instList);
            } else if (isConversion) {
                deleteInstruction(handle, instList);
            } else if (isGoto) {
                GotoInstruction inst = (GotoInstruction) handle.getInstruction();
                InstructionHandle instH = inst.getTarget();
                if (instH == handle.getNext()) {
                    System.out.println("Removed goto");
                    deleteInstruction(handle, instList);
                }
            } else if (isComparison) {
                InstructionHandle handleToCheck = handle;
                while ((!(handleToCheck.getInstruction() instanceof GOTO))) {
                    handleToCheck = handleToCheck.getNext();
                    //System.out.println(handleToCheck);
                }

                System.out.println("OUT");
                if (handleToCheck.getInstruction() instanceof GOTO) {
                    System.out.println("IN");
                    InstructionHandle targetHandle = ((GotoInstruction) handleToCheck.getInstruction()).getTarget();
                    //System.out.println(targetHandle);
                    if (targetHandle == null) {
                        targetHandle = handle.getNext();
                        int firstIndex = targetHandle.getPosition();
                        int secondIndex = handleToCheck.getPosition();
                        if (secondIndex > firstIndex) {
                            ((GotoInstruction) (handleToCheck.getInstruction())).setTarget(handle);
                            System.out.println("Found loop");
                            continue;
                        }
                    }

                }
                if (constants >= 2) {
                    removeLDCs(handle, instList, 2);
                } else {
                    removeLDCs(handle, instList, 1);
                }

                IfInstruction inst = (IfInstruction) handle.getInstruction();
                System.out.println("IfStatement Target" + inst.getTarget());
                if (!performLogOp(handle)) {
                    try {
                        instList.delete(handle, inst.getTarget().getPrev());
                    } catch (Exception ignored) {

                    }
                    justDeletedIf = true;
                } else {
                    try {
                        InstructionHandle target = inst.getTarget();
                        System.out.println("ELSE branch deleting" + handle + target);
                        deleteInstruction(handle, instList);
                        deleteInstruction(target, instList);
                        justDeletedIf = true;
                    } catch (Exception e) {
                        System.out.println("Exception ELSE branch");
                    }
                }

            } else if (isLongComparison) {
                Number value1 = constantStack.pop();
                Number value2 = constantStack.pop();
                Number toPush;
                if ((Long) value1 > (Long) value2) {
                    toPush = 1;
                } else if ((Long) value1 < (Long) value2) {
                    toPush = -1;
                } else {
                    toPush = 0;
                }
                constantStack.push(toPush);
                deleteInstruction(handle, instList);
            }
            else if (isConst) {
                Number value = null;
                if (handle.getInstruction() instanceof ICONST) {
                    value = (((ICONST) handle.getInstruction()).getValue());
                } else if (handle.getInstruction() instanceof FCONST) {
                    value = (((FCONST) handle.getInstruction()).getValue());
                } else if (handle.getInstruction() instanceof LCONST) {
                    value = (((LCONST) handle.getInstruction()).getValue());
                } else if (handle.getInstruction() instanceof DCONST) {
                    value = (((DCONST) handle.getInstruction()).getValue());
                }
                constantStack.push(value);
                System.out.println("Pushed: " + value);
                if (justDeletedIf) {
                    System.out.println("Kept CONST instruction for if statements to function");
                    justDeletedIf = false;
                }
            }
            else if (isArithmeticInst) {
                if (constants >= 2) {
                    removeLDCs(handle, instList, 2);
                } else {
                    removeLDCs(handle, instList, 1);
                }
                performArithOp(handle);
                Number topOfStack = constantStack.pop();

                if (topOfStack instanceof Double) {
                    instList.insert(handle, new LDC2_W(cpgen.addDouble((Double) topOfStack)));
                } else if (topOfStack instanceof Long) {
                    instList.insert(handle, new LDC2_W(cpgen.addLong((Long) topOfStack)));
                } else if (topOfStack instanceof Integer) {
                    instList.insert(handle, new LDC(cpgen.addInteger((Integer) topOfStack)));
                } else if (topOfStack instanceof Float) {
                    instList.insert(handle, new LDC(cpgen.addFloat((Float) topOfStack)));
                }

                constantStack.push(topOfStack);
                deleteInstruction(handle, instList);
            } else if (isStore) {
                Number value = constantStack.pop();
                int index = ((StoreInstruction) handle.getInstruction()).getIndex();
                vars.put(index, value);
                deleteInstruction(handle, instList);
            } else if (isLoad) {
                if (!(handle.getInstruction() instanceof ALOAD)) {
                    int index = ((LoadInstruction) handle.getInstruction()).getIndex();
                    Number topOfStack = vars.get(index);
                    System.out.println("Creating constant: " + topOfStack);
                    constants++;
                    constantStack.push(topOfStack);
                    if (topOfStack instanceof Double) {
                        instList.insert(handle, new LDC2_W(cpgen.addDouble((Double) topOfStack)));
                    } else if (topOfStack instanceof Long) {
                        instList.insert(handle, new LDC2_W(cpgen.addLong((Long) topOfStack)));
                    } else if (topOfStack instanceof Integer) {
                        instList.insert(handle, new LDC(cpgen.addInteger((Integer) topOfStack)));
                    } else if (topOfStack instanceof Float) {
                        instList.insert(handle, new LDC(cpgen.addFloat((Float) topOfStack)));
                    }

                    deleteInstruction(handle, instList);
                }
            }
        }
        try {
            instList.setPositions(true);
        } catch (Exception e) {
            System.out.println("Problem setting positions");
        }
        System.out.println("\nRESULT:");
        for (InstructionHandle handle : instList.getInstructionHandles()) {
            System.out.println(handle.toString());
        }
        methodGen.setMaxStack();
        methodGen.setMaxLocals();

        Method newMethod = methodGen.getMethod();

        //replace the method in the original class
        cgen.replaceMethod(method, newMethod);
    }

    public Number getConstantValue(InstructionHandle handle, ConstantPoolGen cpgen)
    {
        Instruction instruction = handle.getInstruction();
        if ((instruction instanceof LDC)) {
            return (Number) (((LDC) handle.getInstruction()).getValue(cpgen));
        } else if (instruction instanceof LDC2_W) {
            return (((LDC2_W) handle.getInstruction()).getValue(cpgen));
        } else if (instruction instanceof BIPUSH) {
            return (((BIPUSH) handle.getInstruction()).getValue());
        } else if (instruction instanceof SIPUSH) {
            return (((SIPUSH) handle.getInstruction()).getValue());
        }
        return null;
    }

    public void optimize()
    {
        ClassGen cgen = new ClassGen(original);
        cgen.setMajor(50);
        ConstantPoolGen cpgen = cgen.getConstantPool();

        //Implement your optimization here

        Method[] methods = cgen.getMethods();

        for (Method m : methods) {
            optimizeMethod(cgen, cpgen, m);
        }
        this.optimized = cgen.getJavaClass();
    }


    public void write(String optimisedFilePath)
    {
        this.optimize();

        try {
            FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
            this.optimized.dump(out);
        } catch (IOException e) {
            //Auto-generated catch block
            e.printStackTrace();
        }
    }
}
