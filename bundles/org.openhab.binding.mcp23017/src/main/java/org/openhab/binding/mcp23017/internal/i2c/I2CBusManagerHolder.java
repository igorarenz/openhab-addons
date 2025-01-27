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
import java.util.HashMap;
import java.util.Map;

/**
 * Only one I2CBusManager per bus!
 * 
 * @author Igor Arenz - Initial contribution
 *
 */
public class I2CBusManagerHolder {

    private static Map<Byte, I2CBusManager> managers = new HashMap<>();

    public static I2CBusManager getBusManager(byte busNumber) throws IOException {

        I2CBusManager res = managers.get(busNumber);
        if (res == null) {
            res = new I2CBusManager(busNumber);
            managers.put(busNumber, res);
        }
        return res;
    }
}
