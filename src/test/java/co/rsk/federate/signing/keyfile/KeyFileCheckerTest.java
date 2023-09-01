/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.federate.signing.keyfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;

/**
 * Created by ajlopez on 29/12/2016.
 */
class KeyFileCheckerTest {
    private static final String KEY_FILE_PATH = "./keyfiletest.txt";

    @BeforeEach
    void init() throws IOException {
        Files.deleteIfExists(Paths.get(KEY_FILE_PATH));
    }

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(Paths.get(KEY_FILE_PATH));
    }

    @Test
    void invalidFileNameIfNull() {
        KeyFileChecker checker = new KeyFileChecker(null);
        assertEquals("Invalid Key File Name", checker.checkKeyFile());
    }

    @Test
    void invalidFileNameIfEmpty() {
        KeyFileChecker checker = new KeyFileChecker("");
        assertEquals("Invalid Key File Name", checker.checkKeyFile());
    }

    @Test
    void fileDoesNotExist() {
        KeyFileChecker checker = new KeyFileChecker("unknown.txt");
        assertEquals("Key File 'unknown.txt' does not exist", checker.checkKeyFile());
    }

    @Test
    void readKeyFromFile() throws IOException {
        this.writeTestKeyFile("bd3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);
        assertTrue(StringUtils.isEmpty(checker.checkKeyFile()));
    }

    @Test
    void invalidKeyFormatInFile() throws IOException {
        this.writeTestKeyFile("zz3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);

        assertEquals("Error Reading Key File './keyfiletest.txt'", checker.checkKeyFile());
    }

    @Test
    void invalidPermissions() throws IOException {
        this.writeTestKeyFile("zz3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);

        assertEquals("Invalid key file permissions", checker.checkFilePermissions());
    }

    @Test
    void validPermissions() throws IOException {
        this.writeTestKeyFile("zz3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        Files.setPosixFilePermissions(Paths.get(KEY_FILE_PATH), Sets.newSet(PosixFilePermission.OWNER_READ));
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);

        assertEquals("", checker.checkFilePermissions());
    }

    private void writeTestKeyFile(String key) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(KEY_FILE_PATH));
        writer.println(key);
        writer.close();
    }
}
