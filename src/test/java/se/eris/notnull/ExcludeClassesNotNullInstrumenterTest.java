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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import se.eris.maven.NopLogWrapper;
import se.eris.notnull.instrumentation.ClassMatcher;
import se.eris.util.ReflectionUtil;
import se.eris.util.compile.CompileUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Tests to verify that package exclusion works.
 */
public class ExcludeClassesNotNullInstrumenterTest {

    private static final File SRC_DIR = new File("src/test/data");
    private static final File CLASSES_DIR = new File("target/test/data/classes");
    private static final String TEST_CLASS = "TestExclude";

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void beforeClass() {
        final File fileToCompile = new File(SRC_DIR, "se/eris/exclude/" + TEST_CLASS + ".java");
        CompileUtil.compile(CLASSES_DIR, fileToCompile);

        final ExcludeConfiguration excludeConfiguration = new ExcludeConfiguration(Collections.singleton(ClassMatcher.namePattern("se.eris.exclude.*")));
        final Configuration configuration = new Configuration(true, new AnnotationConfiguration(), excludeConfiguration);
        final NotNullInstrumenter instrumenter = new NotNullInstrumenter(new NopLogWrapper());
        final File classesDirectory = new File(CLASSES_DIR, "se/eris/exclude");
        final int numberOfInstrumentedFiles = instrumenter.addNotNullAnnotations(classesDirectory.toPath(), configuration, Collections.<URL>emptyList());

        assertThat(numberOfInstrumentedFiles, greaterThan(0));
    }

    @Test
    public void annotatedParameter_shouldValidate() throws Exception {
        final Class<?> c = CompileUtil.getCompiledClass(CLASSES_DIR, "se.eris.exclude." + TEST_CLASS);
        final Method notNullParameterMethod = c.getMethod("notNullParameter", String.class);

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Argument 0 for @NotNull parameter of se/eris/exclude/" + TEST_CLASS + ".notNullParameter must not be null");
        ReflectionUtil.simulateMethodCall(notNullParameterMethod, new Object[]{null});
    }

    @Test
    public void unAnnotatedParameter_shouldNotValidate() throws Exception {
        final Class<?> c = CompileUtil.getCompiledClass(CLASSES_DIR, "se.eris.exclude." + TEST_CLASS);
        final Method notNullParameterMethod = c.getMethod("unAnnotatedParameter", String.class);

        ReflectionUtil.simulateMethodCall(notNullParameterMethod, new Object[]{null});
    }

    @Test
    public void notnullReturn_shouldNotValidate() throws Exception {
        final Class<?> c = CompileUtil.getCompiledClass(CLASSES_DIR, "se.eris.exclude." + TEST_CLASS);
        final Method notNullReturnMethod = c.getMethod("notNullReturn", String.class);

        exception.expect(IllegalStateException.class);
        exception.expectMessage("NotNull method se/eris/exclude/" + TEST_CLASS + ".notNullReturn must not return null");
        ReflectionUtil.simulateMethodCall(notNullReturnMethod, new Object[]{null});
    }

    @Test
    public void unAnnotatedReturn_shouldNotValidate() throws Exception {
        CompileUtil.getCompiledClass(CLASSES_DIR, "se.eris.exclude." + TEST_CLASS);
        final Class<?> c = CompileUtil.getCompiledClass(CLASSES_DIR, "se.eris.exclude." + TEST_CLASS);
        final Method notNullParameterMethod = c.getMethod("unAnnotatedReturn", String.class);

        ReflectionUtil.simulateMethodCall(notNullParameterMethod, new Object[]{null});
    }

}