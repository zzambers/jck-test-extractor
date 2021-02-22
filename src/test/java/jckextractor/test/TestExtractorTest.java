/*
 * The MIT License
 *
 * Copyright 2020 zzambers.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jckextractor.test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jckextractor.FileUtil;
import jckextractor.TestExtractor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author zzambers
 */
public class TestExtractorTest {

    Path tmpDir;
    Path jckDir;
    Path outputDir;

    /* test files */
    Path testSimple = null;
    Path testWithParent = null;
    Path testDirectLib = null;
    Path testJckLib = null;
    Path testTestLib = null;
    /* lib files */
    Path directA = null;
    Path jckAClass = null;
    Path testsAClass = null;
    Path test2Parent = null;
    /* other */
    Path testHtml = null;
    Path linkedByHtml = null;

    public void prepareFakeJck(Path jckDir) throws IOException {
        /* top level dirs */
        Path src = jckDir.resolve("src");
        Path tests = jckDir.resolve("tests");
        Files.createDirectories(src);
        Files.createDirectories(tests);

        /* dirs in src */
        Path srcTests = src.resolve("tests");
        Files.createDirectories(srcTests);
        Path srcShare = src.resolve("share");
        Files.createDirectories(srcShare);

        /* sources with pkg hierarchy directly in src/ */
        Path srcDirctSourcesDir = src.resolve("direct").resolve("pkg");
        Files.createDirectories(srcDirctSourcesDir);
        directA = srcDirctSourcesDir.resolve("DirectA.java");
        List<String> lines = new ArrayList();
        lines.add("package direct.pkg;");
        lines.add("");
        lines.add("public class DirectA {");
        lines.add("}");
        Files.write(directA, lines, Charset.defaultCharset());
        lines.clear();

        /* sources with pkg hierarchy in jck.* */
        Path srcJckSourcesDir = src.resolve("jck.something").resolve("jck").resolve("pkg");
        Files.createDirectories(srcJckSourcesDir);
        jckAClass = srcJckSourcesDir.resolve("JckA.java");
        lines.add("package jck.pkg;");
        lines.add("");
        lines.add("public class JckA {");
        lines.add("}");
        Files.write(jckAClass, lines, Charset.defaultCharset());
        lines.clear();

        /* sources of additional tests files in src */
        Path srcTestsSourcesDir = srcTests.resolve("api").resolve("api_pkg").resolve("testslib");
        Files.createDirectories(srcTestsSourcesDir);
        testsAClass = srcTestsSourcesDir.resolve("TestsA.java");
        lines.add("package testspkg.api.pkg.testslib;");
        lines.add("");
        lines.add("public class TestsA {");
        lines.add("}");
        Files.write(testsAClass, lines, Charset.defaultCharset());
        lines.clear();

        /* sources of test1 (simple test) */
        Path test1SourcesDir = tests.resolve("api").resolve("api_pkg").resolve("test1");
        Files.createDirectories(test1SourcesDir);
        testSimple = test1SourcesDir.resolve("Test1.java");
        lines.add("package testspkg.api.pkg.test1pkg;");
        lines.add("");
        lines.add("public abstract class Test1 {");
        lines.add("}");
        Files.write(testSimple, lines, Charset.defaultCharset());
        lines.clear();

        /* sources of test2 (dependency in parent dir)
           parent class*/
        Path test2ParentSourcesDir = tests.resolve("api").resolve("api_pkg").resolve("test2parent");
        Files.createDirectories(test2ParentSourcesDir);
        test2Parent = test2ParentSourcesDir.resolve("Test2Parent.java");
        lines.add("package testspkg.api.pkg.test2parentpkg;");
        lines.add("");
        lines.add("public abstract class Test2Parent {");
        lines.add("}");
        Files.write(test2Parent, lines, Charset.defaultCharset());
        lines.clear();
        /* test class */
        Path test2SourcesDir = test2ParentSourcesDir.resolve("test2");
        Files.createDirectories(test2SourcesDir);
        testWithParent = test2SourcesDir.resolve("Test2.java");
        lines.add("package testspkg.api.pkg.test2parentpkg.test2pkg;");
        lines.add("");
        lines.add("import testspkg.api.pkg.test2parentpkg.Test2Parent;");
        lines.add("");
        lines.add("public class Test2 extends Test2Parent {");
        lines.add("}");
        Files.write(testWithParent, lines, Charset.defaultCharset());
        lines.clear();

        Path testDirectLibSourcesDir = tests.resolve("api").resolve("api_pkg").resolve("testDirecLib");
        Files.createDirectories(testDirectLibSourcesDir);
        testDirectLib = testDirectLibSourcesDir.resolve("TestDirectLib.java");
        lines.add("package testspkg.api.pkg.testDirecLib;");
        lines.add("");
        lines.add("import direct.pkg.DirectA;");
        lines.add("");
        lines.add("public class TestDirectLib {");
        lines.add("    DirectA directA;");
        lines.add("}");
        Files.write(testDirectLib, lines, Charset.defaultCharset());
        lines.clear();

        Path testJckLibSourcesDir = tests.resolve("api").resolve("api_pkg").resolve("testJckLib");
        Files.createDirectories(testJckLibSourcesDir);
        testJckLib = testJckLibSourcesDir.resolve("TestJckLib.java");

        lines.add("package testspkg.api.pkg.testJckLib;");
        lines.add("");
        lines.add("import jck.pkg.JckA;");
        lines.add("");
        lines.add("public class TestJckLib {");
        lines.add("    JckA jckA;");
        lines.add("}");
        Files.write(testJckLib, lines, Charset.defaultCharset());
        lines.clear();

        Path testTestLibSourcesDir = tests.resolve("api").resolve("api_pkg").resolve("testTestLib");
        Files.createDirectories(testTestLibSourcesDir);
        testTestLib = testTestLibSourcesDir.resolve("TestTestLib.java");

        lines.add("package testspkg.api.pkg.testTestLib;");
        lines.add("");
        lines.add("import testspkg.api.pkg.testslib.TestsA;");
        lines.add("");
        lines.add("public class TestTestLib {");
        lines.add("    TestsA testsA;");
        lines.add("}");
        Files.write(testTestLib, lines, Charset.defaultCharset());
        lines.clear();

        Path testKshDepSourcesDir = tests.resolve("api").resolve("api_pkg").resolve("testKshDep");
        Files.createDirectories(testKshDepSourcesDir);
        Path testKshDepShell = testKshDepSourcesDir.resolve("testKshDep.ksh");

        lines.add("#!/bin/ksh");
        lines.add(" bin/java -somearg=direct.pkg.DirectA -arg2 jck.pkg.JckA ");
        Files.write(testKshDepShell, lines, Charset.defaultCharset());
        lines.clear();

        /* html test parent directory
           with linked file */
        Path htmlTestParentDir = tests.resolve("api").resolve("api_pkg").resolve("htmlTestParent");
        Files.createDirectories(htmlTestParentDir);
        linkedByHtml = htmlTestParentDir.resolve("linked.txt");
        lines.add("hi!");
        Files.write(linkedByHtml, lines, Charset.defaultCharset());
        lines.clear();
        /* test class */
        Path htmlTestDir = htmlTestParentDir.resolve("testHtml");
        Files.createDirectories(htmlTestDir);
        testHtml = htmlTestDir.resolve("test.html");
        lines.add("<!DOCTYPE HTML>");
        lines.add("<html>");
        lines.add("<head>");
        lines.add("</head>");
        lines.add("<body>");
        lines.add("<a href=\"../linked.txt\">../linked.txt</a>");
        lines.add("</body>");
        lines.add("</html>");
        Files.write(testHtml, lines, Charset.defaultCharset());
        lines.clear();
    }

    public Path getOutputFor(Path p) {
        return outputDir.resolve(jckDir.relativize(p));
    }

    public void AssertExtracted(Path p, boolean b) {
        Path output = getOutputFor(p);
        boolean exists = Files.exists(output);
        Assert.assertEquals("File exists: " + output.toString(), b, exists);
    }

    @Before
    public void before() throws IOException {
        tmpDir = Files.createTempDirectory("jck-test-extr-test");
        jckDir = tmpDir.resolve("fake-jck");
        Files.createDirectories(jckDir);
        outputDir = tmpDir.resolve("output");
        Files.createDirectories(outputDir);
        prepareFakeJck(jckDir);
    }

    @After
    public void after() throws IOException {
        FileUtil.recursiveDelete(tmpDir);
        tmpDir = null;
        jckDir = null;
        outputDir = null;
    }

    public void runExtractor(String testName) throws Exception {
        String[] args = new String[6];
        args[0] = "--jck-dir";
        args[1] = jckDir.toString();
        args[2] = "--output-dir";
        args[3] = outputDir.toString();
        args[4] = "--test";
        args[5] = testName;
        TestExtractor.main(args);
    }

    @Test
    public void testSimple() throws Exception {
        runExtractor("api/api_pkg/test1");
        AssertExtracted(testSimple, true);
        AssertExtracted(directA, false);
        AssertExtracted(jckAClass, false);
        AssertExtracted(testsAClass, false);
        AssertExtracted(test2Parent, false);
        AssertExtracted(testHtml, false);
        AssertExtracted(linkedByHtml, false);
    }

    @Test
    public void testParent() throws Exception {
        runExtractor("api/api_pkg/test2parent/test2");
        AssertExtracted(testWithParent, true);
        AssertExtracted(directA, false);
        AssertExtracted(jckAClass, false);
        AssertExtracted(testsAClass, false);
        AssertExtracted(test2Parent, true);
        AssertExtracted(testHtml, false);
        AssertExtracted(linkedByHtml, false);
    }

    @Test
    public void testDirectLib() throws Exception {
        runExtractor("api/api_pkg/testDirecLib");
        AssertExtracted(testDirectLib, true);
        AssertExtracted(directA, true);
        AssertExtracted(jckAClass, false);
        AssertExtracted(testsAClass, false);
        AssertExtracted(test2Parent, false);
        AssertExtracted(testHtml, false);
        AssertExtracted(linkedByHtml, false);
    }

    @Test
    public void testJckLib() throws Exception {
        runExtractor("api/api_pkg/testJckLib");
        AssertExtracted(testJckLib, true);
        AssertExtracted(directA, false);
        AssertExtracted(jckAClass, true);
        AssertExtracted(testsAClass, false);
        AssertExtracted(test2Parent, false);
        AssertExtracted(testHtml, false);
        AssertExtracted(linkedByHtml, false);
    }

    @Test
    public void testTestLib() throws Exception {
        runExtractor("api/api_pkg/testTestLib");
        AssertExtracted(testTestLib, true);
        AssertExtracted(directA, false);
        AssertExtracted(jckAClass, false);
        AssertExtracted(testsAClass, true);
        AssertExtracted(test2Parent, false);
        AssertExtracted(testHtml, false);
        AssertExtracted(linkedByHtml, false);
    }

    @Test
    public void testKshDep() throws Exception {
        runExtractor("api/api_pkg/testKshDep");
        AssertExtracted(testTestLib, false);
        AssertExtracted(directA, true);
        AssertExtracted(jckAClass, true);
        AssertExtracted(testsAClass, false);
        AssertExtracted(test2Parent, false);
        AssertExtracted(testHtml, false);
        AssertExtracted(linkedByHtml, false);
    }

    @Test
    public void testHtml() throws Exception {
        runExtractor("api/api_pkg/htmlTestParent/testHtml");
        AssertExtracted(testTestLib, false);
        AssertExtracted(directA, false);
        AssertExtracted(jckAClass, false);
        AssertExtracted(testsAClass, false);
        AssertExtracted(test2Parent, false);
        AssertExtracted(testHtml, true);
        AssertExtracted(linkedByHtml, true);
    }

}
