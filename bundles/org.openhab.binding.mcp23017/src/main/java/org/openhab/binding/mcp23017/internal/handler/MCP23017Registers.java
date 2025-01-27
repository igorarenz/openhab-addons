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
package org.openhab.binding.mcp23017.internal.handler;

/**
 * 
 * @author Igor Arenz
 *
 */
public class MCP23017Registers {

    // I/O Direction Registers
    public static final byte IODIRA = 0x00; // I/O Direction Register for Port A
    public static final byte IODIRB = 0x01; // I/O Direction Register for Port B

    // Input Polarity Registers
    public static final byte IPOLA = 0x02; // Input Polarity Register for Port A
    public static final byte IPOLB = 0x03; // Input Polarity Register for Port B

    // byteerrupt Enable Registers
    public static final byte GPINTENA = 0x04; // interrupt-on-Change Enable Register for Port A
    public static final byte GPINTENB = 0x05; // interrupt-on-Change Enable Register for Port B

    // Default Value Registers
    public static final byte DEFVALA = 0x06; // Default Compare Register for interrupt-on-Change for Port A
    public static final byte DEFVALB = 0x07; // Default Compare Register for interrupt-on-Change for Port B

    // interrupt Control Registers
    public static final byte INTCONA = 0x08; // interrupt Control Register for Port A
    public static final byte INTCONB = 0x09; // interrupt Control Register for Port B

    // I/O Configuration Register
    public static final byte IOCON = 0x0A; // I/O Expander Configuration Register (shared for both ports)

    // Pull-up Resistor Configuration Registers
    public static final byte GPPUA = 0x0C; // GPIO Pull-up Resistor Register for Port A
    public static final byte GPPUB = 0x0D; // GPIO Pull-up Resistor Register for Port B

    // interrupt Flag Registers
    public static final byte INTFA = 0x0E; // interrupt Flag Register for Port A
    public static final byte INTFB = 0x0F; // interrupt Flag Register for Port B

    // interrupt Captured Value Registers
    public static final byte INTCAPA = 0x10; // interrupt Captured Value for Port A
    public static final byte INTCAPB = 0x11; // interrupt Captured Value for Port B

    // GPIO Registers
    public static final byte GPIOA = 0x12; // General Purpose I/O Port Register for Port A
    public static final byte GPIOB = 0x13; // General Purpose I/O Port Register for Port B

    // Output Latch Registers
    public static final byte OLATA = 0x14; // Output Latch Register for Port A
    public static final byte OLATB = 0x15; // Output Latch Register for Port B

    // Banked Mode Addresses (Optional, depending on IOCON.BANK setting)
    public static final byte IODIR = 0x00; // I/O Direction Register (Banked Mode)
    public static final byte IPOL = 0x01; // Input Polarity Register (Banked Mode)
    public static final byte GPINTEN = 0x02; // interrupt-on-Change Enable Register (Banked Mode)
    public static final byte DEFVAL = 0x03; // Default Value Register (Banked Mode)
    public static final byte INTCON = 0x04; // interrupt Control Register (Banked Mode)
    public static final byte IOCON_BANKED = 0x05; // Configuration Register (Banked Mode)
    public static final byte GPPU = 0x06; // Pull-up Resistor Register (Banked Mode)
    public static final byte INTF = 0x07; // interrupt Flag Register (Banked Mode)
    public static final byte INTCAP = 0x08; // interrupt Captured Value Register (Banked Mode)
    public static final byte GPIO = 0x09; // GPIO Register (Banked Mode)
    public static final byte OLAT = 0x0A; // Output Latch Register (Banked Mode)

    private MCP23017Registers() {
        // Prevent instantiation
    }
}
