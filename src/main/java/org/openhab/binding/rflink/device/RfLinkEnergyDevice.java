/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.device;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.rflink.RfLinkBindingConstants;
import org.openhab.binding.rflink.message.RfLinkMessage;

/**
 * RfLink data class for energy message.
 *
 * @author Cyril Cauchois - Initial contribution
 */
public class RfLinkEnergyDevice extends RfLinkAbstractDevice {

    private static float WATTS_TO_AMPS_CONVERSION_FACTOR = 230F;

    private static final String KEY_INSTANT_POWER = "WATT";
    private static final String KEY_TOTAL_POWER = "KWATT";

    private static final Collection<String> KEYS = Arrays.asList(KEY_INSTANT_POWER, KEY_TOTAL_POWER);

    public double instantAmps = 0;
    public double totalAmpHours = 0;
    public double instantPower = 0;
    public double totalUsage = 0;

    public RfLinkEnergyDevice() {

    }

    @Override
    public ThingTypeUID getThingType() {
        return RfLinkBindingConstants.THING_TYPE_ENERGY;
    }

    @Override
    public String toString() {
        String str = "";

        str += super.toString();
        str += ", Instant Power = " + instantPower;
        str += ", Total Usage = " + totalUsage;
        str += ", Instant Amps = " + instantAmps;
        str += ", Total Amp Hours = " + totalAmpHours;

        return str;
    }

    @Override
    public void initializeFromMessage(RfLinkMessage message) {
        super.initializeFromMessage(message);
        Map<String, String> values = getMessage().getAttributes();
        // all usage is reported in Watts based on 230V
        if (values.containsKey(KEY_INSTANT_POWER)) {
            instantPower = RfLinkDataParser.parseHexaToUnsignedInt(values.get(KEY_INSTANT_POWER));
            instantAmps = instantPower / WATTS_TO_AMPS_CONVERSION_FACTOR;
        }

        if (values.containsKey(KEY_TOTAL_POWER)) {
            totalUsage = RfLinkDataParser.parseHexaToUnsignedInt(values.get(KEY_TOTAL_POWER));
            totalAmpHours = totalUsage / WATTS_TO_AMPS_CONVERSION_FACTOR;
        }
    }

    @Override
    public Predicate<RfLinkMessage> eligibleMessageFunction() {
        return (message) -> {
            return !Collections.disjoint(message.getAttributesKeys(), KEYS);
        };
    }

    @Override
    public Map<String, State> getStates() {
        Map<String, State> map = new HashMap<>();
        map.put(RfLinkBindingConstants.CHANNEL_INSTANT_POWER, new DecimalType(instantPower));
        map.put(RfLinkBindingConstants.CHANNEL_INSTANT_AMPS, new DecimalType(instantAmps));
        map.put(RfLinkBindingConstants.CHANNEL_TOTAL_AMP_HOURS, new DecimalType(totalAmpHours));
        map.put(RfLinkBindingConstants.CHANNEL_TOTAL_USAGE, new DecimalType(totalUsage));
        return map;
    }

}
