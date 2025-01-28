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

import static org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants.ADDRESS;
import static org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants.BUS_NUMBER;
import static org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants.CHANNEL_GROUP_INPUT;
import static org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants.CHANNEL_GROUP_OUTPUT;
import static org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants.SUPPORTED_CHANNELS;
import static org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants.SUPPORTED_CHANNEL_GROUPS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.openhab.binding.mcp23017.internal.Mcp23017BindingConstants;
import org.openhab.binding.mcp23017.internal.i2c.I2CBusManager;
import org.openhab.binding.mcp23017.internal.i2c.I2CBusManagerHolder;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Mcp23017Handler} is base class for MCP23017 chip support
 *
 * @author Anatol Ogorek - Initial contribution
 * @author Igor Arenz - Rebuild for communication via libc instead of Pi4j
 */
public class Mcp23017Handler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private byte i2cAddress;
    private byte i2cBusNumber;
    private I2CBusManager i2cBusManager;

    /**
     * the polling interval mcp23071 check interrupt register (optional, defaults to 50ms)
     */
    private static final int POLLING_INTERVAL = 50;

    // Pair<BankName, PinNo> -> Channel
    private Map<Pair<String, Byte>, ChannelUID> inputChannels = new HashMap<>();
    private Map<Pair<String, Byte>, ChannelUID> outputChannels = new HashMap<>();

    // Register-Values
    byte IODIRA = 0;
    byte IODIRB = 0;
    byte GPPUA = 0;
    byte GPPUB = 0;
    byte OLATA = 0;
    byte OLATB = 0;

    public Mcp23017Handler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command: {} on channelGroup {} on channel {}", command.toFullString(),
                channelUID.getGroupId(), channelUID.getIdWithoutGroup());

        if (!verifyChannel(channelUID)) {
            return;
        }

        String channelGroup = channelUID.getGroupId();

        try {
            switch (channelGroup) {
                case CHANNEL_GROUP_INPUT:
                    handleInputCommand(channelUID, command);
                    break;
                case CHANNEL_GROUP_OUTPUT:
                    handleOutputCommand(channelUID, command);
                default:
                    break;
            }
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "An exception communication. Exception: " + e.getMessage());
        }
    }

    @Override
    public void initialize() {
        try {
            checkConfiguration();

            this.i2cBusManager = I2CBusManagerHolder.getBusManager(i2cBusNumber);
            startPollingThread();
            updateStatus(ThingStatus.ONLINE);
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "An exception communication. Exception: " + e.getMessage());
        } catch (IllegalArgumentException | SecurityException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "An exception occurred while adding pin. Check pin configuration. Exception: " + e.getMessage());
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        // TODO: Kill thread
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        synchronized (this) {
            logger.debug("channel linked {}", channelUID.getAsString());
            if (!verifyChannel(channelUID)) {
                return;
            }
            String channelGroup = channelUID.getGroupId();

            try {
                if (channelGroup != null) {
                    if (channelGroup.equals(CHANNEL_GROUP_INPUT)) {
                        initializeInputPin(channelUID);
                    }

                    if (channelGroup.equals(CHANNEL_GROUP_OUTPUT)) {
                        initializeOutputPin(channelUID);
                    }
                }
                super.channelLinked(channelUID);
            } catch (IllegalArgumentException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Exception: " + e.getMessage());
            } catch (IOException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "An exception communication. Exception: " + e.getMessage());
            }

        }
    }

    private void initializeInputPin(ChannelUID channel) throws IOException {
        logger.debug("initializing input pin for channel {}", channel.getAsString());

        String pullMode = Mcp23017BindingConstants.PULL_MODE_DEFAULT;
        if (thing.getChannel(channel.getId()) != null) {
            Configuration configuration = thing.getChannel(channel.getId()).getConfiguration();
            pullMode = ((String) configuration.get(Mcp23017BindingConstants.PULL_MODE)) != null
                    ? ((String) configuration.get(Mcp23017BindingConstants.PULL_MODE))
                    : Mcp23017BindingConstants.PULL_MODE_DEFAULT;
        }

        boolean pullModeFlag = pullMode.equalsIgnoreCase(Mcp23017BindingConstants.PULL_MODE_UP);

        logger.debug("initializing pin {}, pullMode {}", channel.getIdWithoutGroup(), pullModeFlag);

        if (outputChannels.containsKey(parsePinName(channel))) {
            throw new IllegalArgumentException("Pin cant be used as input and output at the same time! " + channel);
        }

        Pair<String, Byte> pinId = parsePinName(channel);
        inputChannels.put(pinId, channel);

        byte pinNo = pinId.getRight();
        switch (pinId.getLeft()) {
            case "A":
                IODIRA |= 1 << pinNo;
                break;
            case "B":
                IODIRB |= 1 << pinNo;
                break;
        }

        if (pullModeFlag) {
            switch (pinId.getLeft()) {
                case "A":
                    GPPUA |= 1 << pinNo;
                    break;
                case "B":
                    GPPUB |= 1 << pinNo;
                    break;
            }
        }

        configurePins();

        logger.debug("Bound digital input for PIN: {}, ItemName: {}, pullMode: {}", channel.getIdWithoutGroup(),
                channel.getAsString(), pullMode);
    }

    private void initializeOutputPin(ChannelUID channel) throws IOException {
        logger.debug("initializing output pin for channel {}", channel.getAsString());

        Configuration configuration = thing.getChannel(channel.getId()).getConfiguration();

        // PinState pinState = PinState.valueOf((String) configuration.get(DEFAULT_STATE));
        // logger.debug("initializing for pinState {}", pinState);

        boolean pinStateHigh = false; // TODO Default berücksichtigen
        String pinName = channel.getIdWithoutGroup(); // like A0 or B7

        if (inputChannels.containsKey(parsePinName(channel))) {
            throw new IllegalArgumentException("Pin cant be used as input and output at the same time! " + channel);
        }

        setOutputPinState(channel, pinStateHigh);
        configurePins();
        setOutputLatches();
        logger.debug("Bound digital output for PIN: {}, channel: {}, pinState: {}", pinName, channel, pinStateHigh);
    }

    private void handleGpioPinDigitalStateChangeEvent(String bankName, byte pinNo, boolean pinIsHigh) {
        ChannelUID channel = inputChannels.get(Pair.of(bankName, pinNo));
        if (channel != null) {
            OpenClosedType state = pinIsHigh ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            logger.debug("updating channel {} with state {}", channel, state);

            updateState(channel, state);
        }
    }

    /**
     * A1 -> ("A", 1)
     */
    private Pair<String, Byte> parsePinName(ChannelUID channel) {
        String pinName = channel.getIdWithoutGroup();
        String bankName = pinName.substring(0, 1);
        byte pinNo = Byte.parseByte(pinName.substring(1));
        Pair<String, Byte> res = Pair.of(bankName, pinNo);
        return res;
    }

    private void startPollingThread() {
        Thread pollingThread = new Thread(new Runnable() {

            private Map<String, Byte> lastInputValues = new HashMap<>();

            /**
             * Läd die Pin-States einer Bank vom Chip-Register.
             * Prüft, ob sich bits zur lettzen abfrage geändert haben.
             * Sendet Chanel-Update bei änderungen.
             * 
             * @param bankName
             * @param gpioRegisterAddress
             * @throws IOException
             */
            private void handleBank(String bankName, byte gpioRegisterAddress) throws IOException {

                byte valuesBank = i2cBusManager.readRegister(i2cAddress, gpioRegisterAddress);
                byte lastValuesBank = lastInputValues.get(bankName);
                if (valuesBank != lastValuesBank) {
                    // logger.debug("Values Bank {} changed! {} {}", bankName, valuesBank, lastValuesBank);

                    int changedBits = valuesBank ^ lastValuesBank;

                    for (byte pinNo = 0; pinNo < 8; pinNo++) {
                        if ((changedBits & (1 << pinNo)) > 0) {
                            // Bit pinNo hat sich geändert
                            boolean pinIsHigh = (valuesBank & (1 << pinNo)) > 0;
                            handleGpioPinDigitalStateChangeEvent(bankName, pinNo, pinIsHigh);
                        }
                    }

                    lastInputValues.put(bankName, valuesBank);
                }
            }

            @Override
            public void run() {

                logger.info("Thread started!");

                lastInputValues.put("A", (byte) 0);
                lastInputValues.put("B", (byte) 0);

                Exception lastException = null;
                int lastRegisterCheck = 0;
                while (true) {
                    try {
                        if (IODIRA != 0) {
                            handleBank("A", MCP23017Registers.GPIOA);
                        }

                        if (IODIRB != 0) {
                            handleBank("B", MCP23017Registers.GPIOB);
                        }

                        if (lastRegisterCheck > 100) {
                            // der letzte Check der Register ist mehr als 100 Poll-Intervals her

                            boolean allOk = true;
                            allOk &= checkRegister(MCP23017Registers.IODIRA, IODIRA);
                            allOk &= checkRegister(MCP23017Registers.IODIRB, IODIRB);

                            allOk &= checkRegister(MCP23017Registers.GPPUA, GPPUA);
                            allOk &= checkRegister(MCP23017Registers.GPPUB, GPPUB);

                            allOk &= checkRegister(MCP23017Registers.OLATA, OLATA);
                            allOk &= checkRegister(MCP23017Registers.OLATB, OLATB);

                            if (allOk) {
                                lastRegisterCheck = 0;
                            } // else: im nächsten Durchgang nochmal testen

                        }
                        lastRegisterCheck++;

                        if (lastException != null) {
                            // change back to good
                            updateStatus(ThingStatus.ONLINE);
                            lastException = null;
                        }

                    } catch (IOException e) {
                        if (lastException == null) {
                            logger.error("Error while polling MCP23017 {}", thing, e);
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                    "Error in Polling-Thread! " + e.getMessage());
                            lastException = e;
                        }
                    }

                    try {
                        Thread.sleep(POLLING_INTERVAL);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                }
            }

            private boolean checkRegister(byte register, byte expectedValue) throws IOException {
                byte curValue = i2cBusManager.readRegister(i2cAddress, register);
                if (curValue != expectedValue) {
                    logger.warn("I2C {}/{} : Found unwanted value in register {} Found: {} Expected:{}", i2cBusNumber,
                            i2cAddress, register, curValue, expectedValue);
                    i2cBusManager.writeRegister(i2cAddress, register, expectedValue);
                    return false;
                }
                return true;
            }
        });
        pollingThread.start();
    }

    private boolean verifyChannel(ChannelUID channelUID) {
        if (!isChannelGroupValid(channelUID) || !isChannelValid(channelUID)) {
            logger.warn("Channel group or channel is invalid. Probably configuration problem");
            return false;
        }
        return true;
    }

    private void handleOutputCommand(ChannelUID channelUID, Command command) throws IOException {
        if (command instanceof OnOffType) {
            // GpioPinDigitalOutput outputPin = pinStateHolder.getOutputPin(channelUID);
            Configuration configuration = this.getThing().getChannel(channelUID.getId()).getConfiguration();

            boolean pinHighState = command == OnOffType.ON;

            // invertLogic is null if not configured
            String activeLowStr = Objects.toString(configuration.get(Mcp23017BindingConstants.ACTIVE_LOW), null);
            boolean activeLowFlag = Mcp23017BindingConstants.ACTIVE_LOW_ENABLED.equalsIgnoreCase(activeLowStr);
            pinHighState = pinHighState ^ activeLowFlag;

            logger.debug("got output pin {} for channel {} and command {} [active_low={}, new_state={}]",
                    channelUID.getIdWithoutGroup(), channelUID, command, activeLowFlag, pinHighState);

            setOutputPinState(channelUID, pinHighState);
            setOutputLatches();
        }
    }

    private void handleInputCommand(ChannelUID channelUID, Command command) {
        logger.debug("Nothing to be done in handleCommand for contact.");
    }

    private boolean isChannelGroupValid(ChannelUID channelUID) {
        if (!channelUID.isInGroup()) {
            logger.debug("Defined channel not in group: {}", channelUID.getAsString());
            return false;
        }
        boolean channelGroupValid = SUPPORTED_CHANNEL_GROUPS.contains(channelUID.getGroupId());
        logger.debug("Defined channel in group: {}. Valid: {}", channelUID.getGroupId(), channelGroupValid);

        return channelGroupValid;
    }

    private boolean isChannelValid(ChannelUID channelUID) {
        boolean channelValid = SUPPORTED_CHANNELS.contains(channelUID.getIdWithoutGroup());
        logger.debug("Is channel {} in supported channels: {}", channelUID.getIdWithoutGroup(), channelValid);
        return channelValid;
    }

    protected void checkConfiguration() {
        Configuration configuration = getConfig();
        i2cAddress = Byte.parseByte((configuration.get(ADDRESS)).toString(), 16);
        i2cBusNumber = Byte.parseByte((configuration.get(BUS_NUMBER)).toString());
    }

    private void configurePins() throws IOException {
        i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.IODIRA, IODIRA);
        i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.GPPUA, GPPUA);
        i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.IODIRB, IODIRB);
        i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.GPPUB, GPPUB);
    }

    private void setOutputPinState(ChannelUID channel, boolean high) throws IOException {
        Pair<String, Byte> pinId = parsePinName(channel);
        byte pinNo = pinId.getRight();
        switch (pinId.getLeft()) {
            case "A":
                OLATA = changePin(OLATA, pinNo, high);
                i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.OLATA, OLATA);
                break;
            case "B":
                OLATB = changePin(OLATB, pinNo, high);
                i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.OLATB, OLATB);
                break;
        }
    }

    private byte changePin(byte value, byte pinNo, boolean high) {
        if (high) {
            return (byte) (value | (1 << pinNo));
        } else {
            return (byte) (value & ~(1 << pinNo));
        }
    }

    private void setOutputLatches() throws IOException {
        i2cBusManager.writeRegister(i2cAddress, MCP23017Registers.OLATB, OLATB);
    }
}
