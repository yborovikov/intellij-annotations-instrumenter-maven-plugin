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
import se.eris.maven.NopLogWrapper;
import se.eris.notnull.instrumentation.ClassMatcher;
import se.eris.util.ReflectionUtil;
import se.eris.util.TestCompiler;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

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

}