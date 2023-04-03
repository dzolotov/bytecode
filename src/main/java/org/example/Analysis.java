package org.example;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.*;
import java.lang.reflect.InvocationTargetException;

import static org.objectweb.asm.Opcodes.*;

//class AnalysisVisitor extends ClassVisitor {
//
//    protected AnalysisVisitor() {
//        super(Opcodes.ASM9);
//    }
//
//    @Override
//    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
//        System.out.println("Visit field "+name+" Types "+descriptor);
//        return super.visitField(access, name, descriptor, signature, value);
//    }
//
//    @Override
//    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
//        System.out.println("Method "+name+ " Types "+descriptor);
//        var visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
//        return visitor;
//    }
//
//    @Override
//    public void visitAttribute(Attribute attribute) {
//        System.out.println("Attribute is "+attribute);
//        super.visitAttribute(attribute);
//    }
//
//    @Override
//    public void visitEnd() {
//        System.out.println("Visit end");
//        super.visitEnd();
//    }
//}

public class Analysis {
    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        var reader = new ClassReader(new FileInputStream("build/classes/java/main/org/example/Coin.class"));
        System.out.println("Processing "+reader.getClassName());
//        reader.accept(new AnalysisVisitor(), ClassReader.EXPAND_FRAMES);
        var classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("flip")) {
                var textifier = new Textifier();
                var trackMethodVisitor = new TraceMethodVisitor(textifier);
                for (AbstractInsnNode instruction : method.instructions) {
                    instruction.accept(trackMethodVisitor);
                }
                System.out.println(textifier.text);
            }
        }

        var writer = new ClassWriter(0);
        //создаем public class Calculator extends java.lang.Object
        writer.visit(V19, ACC_PUBLIC + ACC_SUPER, "Calculator", null, "java/lang/Object", null);
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

        var newMethod = writer.visitMethod(ACC_PUBLIC, "sum", "(II)I", null, null);
        var start = new Label();
        var end = new Label();
        //поставим метку (нужна для области видимости локальных переменных)
        newMethod.visitLabel(start);
        //положим два значения в стек из локальных переменных, сложим и вернем результат
        newMethod.visitVarInsn(ILOAD, 1);   //получение значения a
        newMethod.visitVarInsn(ILOAD, 2);   //получение значения b
        newMethod.visitInsn(IADD);
        newMethod.visitInsn(IRETURN);
        newMethod.visitLabel(end);
        //start - end определяет scope для доступности переменной
        newMethod.visitLocalVariable("a", "I", null, start, end, 1);
        newMethod.visitLocalVariable("b", "I", null, start, end, 2);
        //определим размеры стека и локальных переменных
        newMethod.visitMaxs(2, 3);  //стек из двух значений, локальных переменных тоже 2 + this
        //завершаем создание метода
        newMethod.visitEnd();

        var classFile = writer.toByteArray();
        var stream = new FileOutputStream("build/classes/java/main/Calculator.class");
        stream.write(classFile);
        stream.close();

        var calculator = Class.forName("Calculator");
        var calculatorInstance = calculator.getDeclaredConstructor().newInstance();
        System.out.println("Calculator is "+calculator);
        var method = calculator.getMethod("sum", int.class, int.class);
        var result = method.invoke(calculatorInstance, 2, 3);
        assert result instanceof Integer;
        assert (Integer) result ==5;

//        System.out.println("Calculator assert is "+(new Calculator2().sum(2,3)));
    }
}