/*
 * Copyright 2013-2015 Eris IT AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.eris.notnull;

import com.intellij.NotNullInstrumenter;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import se.eris.maven.NopLogWrapper;
import se.eris.notnull.instrumentation.ClassMatcher;
import se.eris.util.ReflectionUtil;
import se.eris.util.TestCompiler;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;

public class AnnotationNotNullInstrumenterTest {

    private static final File SRC_DIR = new File("src/test/data");
    private static final Path CLASSES_DIRECTORY = new File("target/test/data/classes").toPath();

    private static final File TEST_FILE = new File(SRC_DIR, "se/eris/test/TestNotNull.java");

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static TestCompiler compiler;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        compiler = TestCompiler.create(CLASSES_DIRECTORY);
        compiler.compile(TEST_FILE);

        final Configuration configuration = new Configuration(false, new AnnotationConfiguration(notNull(), Collections.<String>emptySet()), new ExcludeConfiguration(Collections.<ClassMatcher>emptySet()));
        final NotNullInstrumenter instrumenter = new NotNullInstrumenter(new NopLogWrapper());
        final int numberOfInstrumentedFiles = instrumenter.addNotNullAnnotations(CLASSES_DIRECTORY, configuration, Collections.<URL>emptyList());

        assertThat(numberOfInstrumentedFiles, greaterThan(0));
    }

    @NotNull
    private static Set<String> notNull() {
        final Set<String> annotations = new HashSet<>();
        annotations.add("org.jetbrains.annotations.NotNull");
        annotations.add("java.lang.Deprecated");
        return annotations;
    }

    @Test
    public void annotatedParameter_shouldValidate() throws Exception {
        final Class<?> c = compiler.getCompiledClass("se.eris.test.TestNotNull");
        final Method notNullParameterMethod = c.getMethod("notNullParameter", String.class);
        ReflectionUtil.simulateMethodCall(notNullParameterMethod, "should work");

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Argument 0 for @NotNull parameter of se/eris/test/TestNotNull.notNullParameter must not be null");
        ReflectionUtil.simulateMethodCall(notNullParameterMethod, new Object[]{null});
    }

    @Test
    public void notnullReturn_shouldValidate() throws Exception {
        final Class<?> c = compiler.getCompiledClass("se.eris.test.TestNotNull");
        final Method notNullReturnMethod = c.getMethod("notNullReturn", String.class);
        ReflectionUtil.simulateMethodCall(notNullReturnMethod, "should work");

        exception.expect(IllegalStateException.class);
        exception.expectMessage("NotNull method se/eris/test/TestNotNull.notNullReturn must not return null");
        ReflectionUtil.simulateMethodCall(notNullReturnMethod, new Object[]{null});
    }

    @Test
    public void annotatedReturn_shouldValidate() throws Exception {
        final Class<?> c = compiler.getCompiledClass("se.eris.test.TestNotNull");
        final Method notNullReturnMethod = c.getMethod("annotatedReturn", String.class);
        ReflectionUtil.simulateMethodCall(notNullReturnMethod, "should work");

        exception.expect(IllegalStateException.class);
        exception.expectMessage("NotNull method se/eris/test/TestNotNull.annotatedReturn must not return null");
        ReflectionUtil.simulateMethodCall(notNullReturnMethod, new Object[]{null});
    }

    @Test
    public void overridingMethod_isInstrumented() throws Exception {
        final Class<?> subargClass = compiler.getCompiledClass("se.eris.test.TestNotNull$Subarg");
        final Class<?> subClass = compiler.getCompiledClass("se.eris.test.TestNotNull$Sub");
        final Method specializedMethod = subClass.getMethod("overload", subargClass);
        assertFalse(specializedMethod.isSynthetic());
        assertFalse(specializedMethod.isBridge());
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Argument 0 for @NotNull parameter of se/eris/test/TestNotNull$Sub.overload must not be null");
        ReflectionUtil.simulateMethodCall(subClass.newInstance(), specializedMethod, new Object[]{null});
    }

    @Test
    public void syntheticMethod_dispatchesToSpecializedMethod() throws Exception {
        final Class<?> superargClass = compiler.getCompiledClass("se.eris.test.TestNotNull$Superarg");
        final Class<?> subClass = compiler.getCompiledClass("se.eris.test.TestNotNull$Sub");
        final Method generalMethod = subClass.getMethod("overload", superargClass);
        assertTrue(generalMethod.isSynthetic());
        assertTrue(generalMethod.isBridge());
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Argument 0 for @NotNull parameter of se/eris/test/TestNotNull$Sub.overload must not be null");
        ReflectionUtil.simulateMethodCall(subClass.newInstance(), generalMethod, new Object[]{null});
    }

    @Test
    public void onlySpecificMethod_isInstrumented() throws Exception {
        // Check that only the specific method has a string annotation indicating instrumentation
        final File f = new File(CLASSES_DIRECTORY.toFile(), "se/eris/test/TestNotNull$Sub.class");
        assertTrue(f.isFile());
        final ClassReader cr = new ClassReader(new FileInputStream(f));
        final List<String> strings = getStringConstants(cr, "overload");
        final String onlyExpectedString = "(Lse/eris/test/TestNotNull$Subarg;)V:" +
                "Argument 0 for @NotNull parameter of " +
                "se/eris/test/TestNotNull$Sub.overload must not be null";
        assertEquals(Collections.singletonList(
                onlyExpectedString), strings);
    }

    @Test
    public void innerClassesSegmentIsPreserved() throws Exception {
        // Check that only the specific method has a string annotation indicating instrumentation
        final File f = new File(CLASSES_DIRECTORY.toFile(), "se/eris/test/TestNotNull$InnerClassesSegmentIsPreserved.class");
        assertTrue(f.isFile());
        final ClassReader cr = new ClassReader(new FileInputStream(f));
        final List<InnerClass> innerClasses = getInnerClasses(cr);
        assertEquals(2, innerClasses.size());
        //self-entry
        assertEquals("se/eris/test/TestNotNull$InnerClassesSegmentIsPreserved", innerClasses.get(0).name);
        //inner entry
        final InnerClass expected = new InnerClass("se/eris/test/TestNotNull$InnerClassesSegmentIsPreserved$ASub",
                "se/eris/test/TestNotNull$InnerClassesSegmentIsPreserved", "ASub", Opcodes.ACC_PUBLIC |
                Opcodes.ACC_STATIC);
        assertEquals(expected
                , innerClasses.get(1));
    }

    private List<InnerClass> getInnerClasses(final ClassReader cr) {
        final List<InnerClass> innerClasses = new ArrayList<>();
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
                innerClasses.add(new InnerClass(name, outerName, innerName, access));
            }
        }, 0);
        return innerClasses;
    }

    @NotNull
    private List<String> getStringConstants(final ClassReader cr, final String methodName) {
        final List<String> strings = new ArrayList<>();
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
                                             final String[] exceptions) {
                if (name.equals(methodName)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitLdcInsn(final Object cst) {
                            if (cst instanceof String) {
                                strings.add(desc + ":" + cst);
                            }
                        }
                    };
                }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        }, 0);
        return strings;
    }

}