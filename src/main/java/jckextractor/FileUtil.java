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
package jckextractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author zzambers
 */
public class FileUtil {

    public static void recursiveCopy(final Path srcDir, final Path targetDir) throws IOException {
        final Path parent = srcDir.getParent();
        final Path srcRelativizeDir = (parent != null) ? parent : srcDir;
        FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path t, BasicFileAttributes bfa) throws IOException {
                Files.createDirectories(targetDir.resolve(srcRelativizeDir.relativize(t)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                Files.copy(t, targetDir.resolve(srcRelativizeDir.relativize(t)));
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(srcDir, fv);
    }

    public static void recursiveDelete(final Path file) throws IOException {
        FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(file, fv);
    }

    public static Path getPath(FileSystem fs, String... path) {
        int pathLength = path.length;
        if (pathLength > 1) {
            return fs.getPath(path[0], Arrays.<String>copyOfRange(path, 1, pathLength));
        }
        if (pathLength == 1) {
            return fs.getPath(path[0]);
        }
        throw new IllegalArgumentException();
    }

    static Set<String> findPattern(Path path, Pattern p) throws IOException {
        List<String> lines = Files.readAllLines(path, Charset.forName("UTF-8"));
        Set<String> matches = new HashSet<>();
        for (String line : lines) {
            Matcher m = p.matcher(line);
            while (m.find()) {
                matches.add(m.group(m.groupCount() > 0 ? 1 : 0));
            }
        }
        return matches;
    }

    static String findPatternFirst(Path path, Pattern p) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    return m.group(m.groupCount() > 0 ? 1 : 0);
                }
            }
        }
        return null;
    }

}
