/*
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 */
package org.apache.rat.walker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.rat.api.Document;
import org.apache.rat.api.RatException;
import org.apache.rat.document.impl.DocumentImplUtils;
import org.apache.rat.report.RatReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DirectoryWalkerTest {

	private static File toWalk;

    private static void fileWriter(File dir, String name, String contents) throws IOException {
        try (FileWriter writer = new FileWriter(new File(dir, name))) {
            writer.write(contents);
            writer.flush();
        }
    }
    @BeforeAll
    public static void setUp(@TempDir File dir) throws Exception {
        toWalk = dir;
        /*
        Create a directory structure like this:

            regular
                regularFile
                .hiddenFile
            .hidden
                regularFile
                .hiddenFile
         */
        File regular = new File(toWalk, "regular");
        regular.mkdir();
        fileWriter(regular, "regularFile", "regular file");
        fileWriter(regular, ".hiddenFile", "hidden file");

        File hidden = new File(toWalk, ".hidden");
        hidden.mkdir();
        fileWriter(hidden, "regularFile", "regular file");
        fileWriter(hidden, ".hiddenFile", "hidden file");
    }

    private String expectedName(String name) {
        return DocumentImplUtils.toName(toWalk)+name;
    }


    
    @Test
    public void noFiltersTest() throws IOException, RatException {
        DirectoryWalker walker = new DirectoryWalker(toWalk, null,null);
        List<String> scanned = new ArrayList<>();
        walker.run(new TestRatReport(scanned));
        String[] expected = {"/regular/regularFile", "/regular/.hiddenFile", "/.hidden/regularFile", "/.hidden/.hiddenFile"};
        assertEquals(4, scanned.size());
        for (String ex : expected) {
            assertTrue(scanned.contains(expectedName(ex)), ()-> String.format("Missing %s", expectedName(ex)));
        }
    }

    @Test
    public void noHiddenFileFiltersTest() throws IOException, RatException {
        DirectoryWalker walker = new DirectoryWalker(toWalk, NameBasedHiddenFileFilter.HIDDEN,null);
        List<String> scanned = new ArrayList<>();
        walker.run(new TestRatReport(scanned));
        String[] expected = {"/regular/regularFile", "/.hidden/regularFile"};
        assertEquals(2, scanned.size());
        for (String ex : expected) {
            assertTrue(scanned.contains(expectedName(ex)), ()-> String.format("Missing %s", expectedName(ex)));
        }
    }

    @Test
    public void noHiddenDirectoryFiltersTest() throws IOException, RatException {
        DirectoryWalker walker = new DirectoryWalker(toWalk, null, NameBasedHiddenFileFilter.HIDDEN);
        List<String> scanned = new ArrayList<>();
        walker.run(new TestRatReport(scanned));
        String[] expected = {"/regular/regularFile", "/regular/.hiddenFile"};
        assertEquals(2, scanned.size());
        for (String ex : expected) {
            assertTrue(scanned.contains(expectedName(ex)), ()-> String.format("Missing %s", expectedName(ex)));
        }
    }

    @Test
    public void noHiddenDirectoryAndNoHiddenFileFiltersTest() throws IOException, RatException {
        DirectoryWalker walker = new DirectoryWalker(toWalk, NameBasedHiddenFileFilter.HIDDEN, NameBasedHiddenFileFilter.HIDDEN);
        List<String> scanned = new ArrayList<>();
        walker.run(new TestRatReport(scanned));
        String[] expected = {"/regular/regularFile"};
        assertEquals(1, scanned.size());
        for (String ex : expected) {
            assertTrue(scanned.contains(expectedName(ex)), ()-> String.format("Missing %s", expectedName(ex)));
        }
    }

    class TestRatReport implements RatReport {

        private List<String> scanned;

        public TestRatReport(List<String> scanned) {
            this.scanned = scanned;
        }

        @Override
        public void startReport() {
            // no-op
        }

        @Override
        public void report(Document document) throws RatException {
            scanned.add(document.getName());
        }

        @Override
        public void endReport() {
            // no-op
        }
    }
}
