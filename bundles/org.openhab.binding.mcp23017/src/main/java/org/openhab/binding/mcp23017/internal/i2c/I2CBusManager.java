/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mcp23017.internal.i2c;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * 
 * @author Igor Arenz - Initial contribution
 *
 */
@NonNullByDefault
public class I2CBusManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final int O_RDWR = 0x0002; // Lesen und schreiben

    private static final Object I2C_LOCK = new Object();

    private static final long TIMEOUT_SECONDS = 5;

    private final int busFileDescriptor;

    private byte i2cBusNumber;

    public I2CBusManager(byte i2cBusNumber) throws IOException {
        this.i2cBusNumber = i2cBusNumber;
        String i2cBusPath = "/dev/i2c-" + i2cBusNumber;
        this.busFileDescriptor = LibC.INSTANCE.open(i2cBusPath, O_RDWR);
        if (this.busFileDescriptor < 0) {
            throw new RuntimeException("Failed to open I2C bus: " + i2cBusPath);
        }
    }

    private void writeToI2C(int fd, byte[] data) {
        int bytesWritten = LibC.INSTANCE.write(fd, data, data.length);
        if (bytesWritten < 0) {
            throw new RuntimeException("Failed to write to file descriptor: " + fd);
        }
    }

    private byte[] readFromI2C(int fd, int length) {
        byte[] buffer = new byte[length];
        int bytesRead = LibC.INSTANCE.read(fd, buffer, length);
        if (bytesRead < 0) {
            throw new RuntimeException("Failed to read from file descriptor: " + fd);
        }
        return buffer;
    }

    public byte readRegister(int address, byte register) throws IOException {
        synchronized (I2C_LOCK) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Byte> future = executor.submit(() -> {
                try {
                    setSlaveAddress(address);
                    writeToI2C(busFileDescriptor, new byte[] { register });
                    byte[] res = readFromI2C(busFileDescriptor, 1);
                    return res[0];
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); // Operation abbrechen
                throw new IOException("Read operation timed out");
            } catch (ExecutionException | InterruptedException e) {
                throw new IOException("Read operation failed", e);
            } finally {
                executor.shutdown();
            }
        }
    }

    public void writeRegister(byte i2cAddress, byte register, byte value) throws IOException {
        logger.debug("Set i2c {} {} {} to {}", i2cBusNumber, Integer.toHexString(i2cAddress),
                Integer.toHexString(register), Integer.toBinaryString(value));

        synchronized (I2C_LOCK) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                try {
                    setSlaveAddress(i2cAddress);
                    writeToI2C(busFileDescriptor, new byte[] { register, value });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); // Operation abbrechen
                throw new IOException("Write operation timed out");
            } catch (ExecutionException | InterruptedException e) {
                throw new IOException("Write operation failed", e);
            } finally {
                executor.shutdown();
            }
        }
    }

    // Setzt die Slave-Adresse für die nächste Operation
    private void setSlaveAddress(int address) throws IOException {
        if (address < 0x03 || address > 0x77) {
            throw new IllegalArgumentException("Invalid I2C address: " + address);
        }
        int ioctlCommand = 0x0703; // I2C_SLAVE-Kommando;
        LibC.INSTANCE.ioctl(busFileDescriptor, ioctlCommand, address);
    }

    // Schließt die Bus-Datei
    public void close() throws IOException {
        synchronized (I2C_LOCK) {
            if (busFileDescriptor >= 0) {
                LibC.INSTANCE.close(busFileDescriptor);
            }
        }
    }

    // Native ioctl-Aufrufe für die Slave-Adresse
    public interface LibC extends Library {
        LibC INSTANCE = Native.load("c", LibC.class);

        int open(String path, int flags);

        int close(int fd);

        int ioctl(int fd, int command, int value);

        int write(int fd, byte[] buffer, int count);

        int read(int fd, byte[] buffer, int count);
    }
}
