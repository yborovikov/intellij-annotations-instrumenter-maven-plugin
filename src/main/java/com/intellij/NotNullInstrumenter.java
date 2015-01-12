package com.intellij;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import se.eris.asm.AsmUtils;
import se.eris.maven.LogWrapper;
import se.eris.notnull.InstrumenterExecutionException;
import se.eris.util.ClassFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class NotNullInstrumenter {

    @NotNull
    private LogWrapper logger;

    public NotNullInstrumenter(@NotNull final LogWrapper logWrapper) {
        logger = logWrapper;
    }

    public int addNotNullAnnotations(@NotNull final String classesDirectory, @NotNull final Set<String> notNullAnnotations,@NotNull final  List<URL> urls) {
        final InstrumentationClassFinder finder = new InstrumentationClassFinder(urls.toArray(new URL[urls.size()]));
        return instrumentDirectoryRecursive(new File(classesDirectory), finder, notNullAnnotations);
    }

    private int instrumentDirectoryRecursive(@NotNull final File classesDirectory, @NotNull final InstrumentationClassFinder finder, @NotNull final Set<String> notNullAnnotations) {
        int instrumentedCounter = 0;
        final Collection<File> classes = ClassFileUtils.getClassFiles(classesDirectory.toPath());
        for (@NotNull final File file : classes) {
            instrumentedCounter += instrumentFile(file, finder, notNullAnnotations);
        }
        return instrumentedCounter;
    }

    private int instrumentFile(@NotNull final File file, @NotNull final InstrumentationClassFinder finder, @NotNull final Set<String> notNullAnnotations)  {
        logger.debug("Adding @NotNull assertions to " + file.getPath());
        try {
            return instrumentClass(file, finder, notNullAnnotations) ? 1 : 0;
        } catch (final IOException e) {
            logger.warn("Failed to instrument @NotNull assertion for " + file.getPath() + ": " + e.getMessage());
        } catch (final RuntimeException e) {
            throw new InstrumenterExecutionException("@NotNull instrumentation failed for " + file.getPath() + ": " + e.toString(), e);
        }
        return 0;
    }

    private static boolean instrumentClass(@NotNull final File file, @NotNull final InstrumentationClassFinder finder, @NotNull final Set<String> notNullAnnotations) throws IOException {
        final FileInputStream inputStream = new FileInputStream(file);
        try {
            final ClassReader classReader = new ClassReader(inputStream);

            final int fileVersion = getClassFileVersion(classReader);

            if (AsmUtils.javaVersionSupportsAnnotations(fileVersion)) {
                final ClassWriter writer = new InstrumenterClassWriter(getAsmClassWriterFlags(fileVersion), finder);

                final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer, notNullAnnotations);
                classReader.accept(instrumenter, 0);
                if (instrumenter.isModification()) {
                    final FileOutputStream fileOutputStream = new FileOutputStream(file);
                    try {
                        fileOutputStream.write(writer.toByteArray());
                        return true;
                    } finally {
                        fileOutputStream.close();
                    }
                }
            }
        } finally {
            inputStream.close();
        }
        return false;
    }

    /**
     * @return the flags for class writer
     */
    private static int getAsmClassWriterFlags(final int version) {
        return AsmUtils.asmOpcodeToJavaVersion(version) >= 6 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
    }

    private static int getClassFileVersion(@NotNull final ClassReader reader) {
        final int[] classFileVersion = new int[1];
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
                classFileVersion[0] = version;
            }
        }, 0);
        return classFileVersion[0];
    }
}