package org.example;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import static org.example.OperationType.*;
import static org.objectweb.asm.Opcodes.*;

enum OperationType {
    ADD,
    SUB,
    MUL,
    DIV,
}

class ElementResult {
    String result;
    int position;

    ElementResult(String result, int position) {
        this.result = result;
        this.position = position;
    }
}


class OperationBuilder {
    OperationBuilder(ClassWriter writer) {
        this.writer = writer;
    }

    ClassWriter writer;

    void createConstructor(String className) {
        //создаем public class Calculator extends java.lang.Object
        writer.visit(V17, ACC_PUBLIC + ACC_SUPER, className, null, "java/lang/Object", null);
        //создаем конструктор без аргументов
        var newConstructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        newConstructor.visitVarInsn(ALOAD, 0);
        //вызываем конструктор родительского класса (не интерфейс, поэтому false последний аргумент)
        //()V - сигнатура метода без аргументов и с типом результата void (V)
        newConstructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        newConstructor.visitInsn(RETURN);
        //определяем размер стека и локальных переменных
        newConstructor.visitMaxs(1, 1);
        //завершаем создание конструктора
        newConstructor.visitEnd();
    }

    void createMethod(String methodName, String math, ArrayList<String> vars) throws IllegalAccessException {
        StringBuilder variables = new StringBuilder();
        for (String var : vars) {
            variables.append("I");
        }
        String descriptor = "(" + variables + ")I";
        System.out.println("Descriptor - " + descriptor);
        var newMethod = writer.visitMethod(ACC_PUBLIC, methodName, descriptor, null, null);
        var start = new Label();
        newMethod.visitLabel(start);
        applyMethod(newMethod, math, 0, vars);
        var end = new Label();
        newMethod.visitLabel(end);
        newMethod.visitInsn(IRETURN);
        for (int i = 1; i <= vars.size(); i++) {
            newMethod.visitLocalVariable(vars.get(i - 1), "I", null, start, end, i);
        }
        newMethod.visitMaxs(maxDepth, 1 + vars.size());  //стек из N значений, локальных переменных тоже 2 + this
        newMethod.visitEnd();
    }

    void saveToClassFile(String className) throws IOException {
        var classFile = writer.toByteArray();
        var stream = new FileOutputStream("build/classes/java/main/" + className + ".class");
        stream.write(classFile);
        stream.close();
    }

    OperationType getType(char symbol) {
        OperationType ot = null;
        switch (symbol) {
            case '+' -> ot = ADD;
            case '-' -> ot = SUB;
            case '*' -> ot = MUL;
            case '/' -> ot = DIV;
        }
        return ot;
    }

    int maxDepth = 0;
    int depth = 0;

    void changeDepth(int delta) {
        depth += delta;
        if (depth > maxDepth) {
            maxDepth = depth;
        }
    }

    void putOperationIfNeeded(MethodVisitor methodVisitor, OperationType operation) {
        if (operation != null) {
            System.out.println("Push operation " + operation.name());
            changeDepth(-1);
            switch (operation) {
                case ADD -> methodVisitor.visitInsn(IADD);
                case SUB -> methodVisitor.visitInsn(ISUB);
                case MUL -> methodVisitor.visitInsn(IMUL);
                case DIV -> methodVisitor.visitInsn(IDIV);
            }
        }
    }

    int applyMethod(MethodVisitor visitor, String math, int currentIndex, ArrayList<String> vars) throws IllegalAccessException {
        OperationType lastOperation = null;
        while (currentIndex < math.length()) {
            if (math.charAt(currentIndex) == ')') {
                //скобки закончились - завершаем операции над стеком и возвращаем следующую позицию
                putOperationIfNeeded(visitor, lastOperation);
                return currentIndex + 1;
            }
            if (math.charAt(currentIndex) == '(') {
                //скобки собираем как отдельное значение в стеке
                currentIndex = applyMethod(visitor, math, currentIndex + 1, vars);
                putOperationIfNeeded(visitor, lastOperation);
                lastOperation = null;
                continue;
            }
            //сохраняем операцию для применения к следующему значению
            var type = getType(math.charAt(currentIndex));
            if (type != null) {
                lastOperation = type;
                currentIndex++;
                continue;
            }
            //извлекаем значение числового операнда или переменной
            StringBuilder operand = new StringBuilder();
            while (currentIndex < math.length() && isDigitOrVar(math, currentIndex)) {
                operand.append(math.charAt(currentIndex));
                currentIndex++;
            }
            try {
                System.out.println("Push constant " + operand);
                changeDepth(1);
                visitor.visitLdcInsn(Integer.parseInt(operand.toString()));
            } catch (Exception e) {
                var varIndex = vars.indexOf(operand.toString());
                if (varIndex < 0) {
                    throw new IllegalAccessException("Variable " + operand + " isn't defined");
                }
                visitor.visitVarInsn(ILOAD, varIndex + 1);
            }
            //применяем операцию над стеком (если необходимо)
            putOperationIfNeeded(visitor, lastOperation);
            //и готовимся к следующей операции
            lastOperation = null;
        }
        return currentIndex;
    }

    boolean isDigitOrVar(String current, int index) {
        var chr = current.charAt(index);
        return (chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'z');
    }

    public ElementResult expandBrackets(String current, int index, int priority) {
        StringBuilder builder = new StringBuilder();
        while (index < current.length()) {
            //завершили выражение - возвращаем подстроку и позицию после скобки
            if (current.charAt(index) == ')') {
                return new ElementResult(builder.toString(), index + 1);
            }
            //начало подвыражения в скобках
            if (current.charAt(index) == '(') {
                var result = expandBrackets(current, index + 1, 1);
                index = result.position;
                builder.append(result.result);
                continue;
            }
            //начало последнего обнаруженного числа
            int lastPosition = index;
            //пропускаем число
            while (index < current.length() && isDigitOrVar(current, index)) index++;
            //определяем приоритет следующей операции
            int myPriority = priority;
            if (index < current.length()) {
                char op = current.charAt(index);
                if (op == '+' || op == '-') myPriority = 1;
                if (op == '*' || op == '/') myPriority = 2;
            }
            if (myPriority > priority) {
                //если больше - оборачиваем в скобки (возможно в несколько пар, если разница приоритетов >1)
                ElementResult subitem = expandBrackets(current, lastPosition, myPriority);
                index = subitem.position;
                String result = subitem.result;
                for (int i = priority; i < myPriority; i++) result = "(" + result + ")";
                builder.append(result);
            } else if (myPriority < priority) {
                //если меньше - просто добавляем число и выходим (продолжаем в предыдущих скобках)
                builder.append(current.substring(lastPosition, index));
                return new ElementResult(builder.toString(), index);
            } else {
                //иначе добавляем к линейной последовательности операторов
                builder.append(current.substring(lastPosition, index));
                if (index < current.length() && current.charAt(index) != ')') {
                    builder.append(current.charAt(index));
                } else {
                    return new ElementResult(builder.toString(), index);
                }
                index++;
            }
        }
        return new ElementResult(builder.toString(), index);
    }
}

public class Analysis2 {

    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        String className = "Calculator2";
        var writer = new ClassWriter(0);
        var builder = new OperationBuilder(writer);
        var result = builder.expandBrackets("x+3*y+20*2-5*3", 0, 1);
        System.out.println("Brackets expansion result is " + result.result);
        builder.createConstructor(className);
        ArrayList<String> variables = new ArrayList<>();
        variables.add("x");
        variables.add("y");
        builder.createMethod("eval", result.result, variables);
        builder.saveToClassFile(className);

        var calculator = Class.forName(className);
        var calculatorInstance = calculator.getDeclaredConstructor().newInstance();
        var method = calculator.getMethod("eval", int.class, int.class);
        var eval = method.invoke(calculatorInstance, 50, 30);
        System.out.print(eval);
    }
}