package me.nov.convert;


import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class BrainfuckToBytecode implements Opcodes {
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("No brainfuck file specified");
      return;
    }
    String brainfuckName = new File(args[0]).getName().split("[.]")[0];
    if (brainfuckName.length() > 1)
      brainfuckName = brainfuckName.substring(0, 1).toUpperCase() + brainfuckName.substring(1);
    brainfuckName = brainfuckName.replaceAll("[^a-zA-Z0-9-_.]", "_"); // escape illegal class names

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    MethodVisitor mv;

    // we can use an old version here as no dynamic instructions are used
    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, brainfuckName, null, "java/lang/Object", null);
    {
      // visit default constructor
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }

    mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null,
            new String[]{"java/io/IOException"});
    mv.visitCode();
    mv.visitLdcInsn(65535);
    mv.visitIntInsn(NEWARRAY, T_CHAR);
    mv.visitVarInsn(ASTORE, 1);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, 2);

    BufferedReader buffer = new BufferedReader(new InputStreamReader(new FileInputStream(args[0])));

    LinkedList<Label> jumpStarts = new LinkedList<>();
    LinkedList<Label> jumpEnds = new LinkedList<>();

    int c;
    int sequence = 0;
    char lastChar = '\0';
    while ((c = buffer.read()) != -1) {
      char character = (char) c;
      if (sequence == 0 || lastChar == character) {
        sequence++;
      } else {
        interpretSequence(jumpStarts, jumpEnds, mv, lastChar, sequence);

        // reset sequence
        sequence = 1;
      }
      lastChar = character;
    }
    interpretSequence(jumpStarts, jumpEnds, mv, lastChar, sequence); // visit last sequence

    if (!jumpStarts.isEmpty()) {
      throw new RuntimeException(jumpStarts.size() + " unclosed loop(s)");
    }

    mv.visitInsn(RETURN);
    mv.visitEnd();
    cw.visitEnd();
    Files.write(new File(brainfuckName + ".class").toPath(), cw.toByteArray());

    System.out.println("Successfully compiled " + brainfuckName + ".class!");
  }

  private static void interpretSequence(LinkedList<Label> jumpStarts, LinkedList<Label> jumpEnds, MethodVisitor mv,
                                        char character, int sequence) {
    switch (character) {
      case '>':
        mv.visitIincInsn(2, sequence);
        break;
      case '<':
        mv.visitIincInsn(2, -sequence);
        break;
      case '+':
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitInsn(DUP2);
        mv.visitInsn(CALOAD);
        pushInt(mv, sequence);
        mv.visitInsn(IADD);
        mv.visitInsn(CASTORE);
        break;
      case '-':
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitInsn(DUP2);
        mv.visitInsn(CALOAD);
        pushInt(mv, sequence);
        mv.visitInsn(ISUB);
        mv.visitInsn(CASTORE);
        break;
      case '.':
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitInsn(CALOAD);
        for (int i = 0; i < sequence; i++) { // who would loop this but whatever, let's optimize this
          if (i < sequence - 1)
            mv.visitInsn(DUP2); // don't dup on last
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(C)V", false);
        }
        break;
      case ',':
        for (int i = 0; i < sequence; i++) {
          mv.visitVarInsn(ALOAD, 1);
          mv.visitVarInsn(ILOAD, 2);
          mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/InputStream", "read", "()I", false);
          mv.visitInsn(CASTORE);
        }
        break;
      case '[':
        for (int i = 0; i < sequence; i++) {
          // start a loop
          Label start = new Label();
          Label end = new Label();
          jumpStarts.add(start);
          jumpEnds.add(end);

          mv.visitLabel(start);
          mv.visitVarInsn(ALOAD, 1);
          mv.visitVarInsn(ILOAD, 2);
          mv.visitInsn(CALOAD);
          mv.visitJumpInsn(IFEQ, end);
        }
        break;
      case ']':
        for (int i = 0; i < sequence; i++) {
          if (jumpStarts.isEmpty()) {
            throw new RuntimeException("Loop end without a started loop");
          }
          // end innermost loop
          mv.visitJumpInsn(GOTO, jumpStarts.removeLast());
          mv.visitLabel(jumpEnds.removeLast());
        }
        break;
    }
  }


  public static void pushInt(MethodVisitor mv, int i) {
    if (i >= -1 && i <= 5) {
      mv.visitInsn(i + ICONST_0);
      return;
    }
    if (i >= -128 && i <= 127) {
      mv.visitIntInsn(BIPUSH, i);
      return;
    }
    // this shouldn't really happen but whatever
    if (i >= -32768 && i <= 32767) {
      mv.visitIntInsn(SIPUSH, i);
      return;
    }
    mv.visitLdcInsn(i);
  }
}
