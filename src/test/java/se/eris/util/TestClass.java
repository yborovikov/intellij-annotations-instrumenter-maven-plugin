package se.eris.util;

import java.io.File;

public class TestClass {

    private final String fullClassName;

    public TestClass(final String fullClassName) {
        this.fullClassName = fullClassName;
    }

    public String getSimpleName() {
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }

    public String getName() {
        return fullClassName;
    }

    // todo replace / with . in messages?
    public String getAsmName() {
        return fullClassName.replace(".", "/");
    }

    public File getFile(final File path) {
        return new File(path, fullClassName.replace(".", "/") + ".java");
    }

    public File getClassFile(final File path) {
        return new File(path, fullClassName.replace(".", "/") + ".class");
    }

    public TestClass inner(final String innerName) {
        return new TestClass(fullClassName + "$" + innerName);
    }
}
