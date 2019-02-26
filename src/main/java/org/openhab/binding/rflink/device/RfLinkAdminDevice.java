/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.device;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * RfLink admin device - aimed to handle and send "raw" messages (keep alive, RTS setup, etc.) on the bridge
 *
 * @author cartemere - Initial contribution
 */
public class RfLinkAdminDevice extends RfLinkAbstractDevice {

    public static RfLinkAdminDevice PING = new RfLinkAdminDevice("10;PING;");

    private String rawMessage = null;

    public RfLinkAdminDevice() {
    }

    public RfLinkAdminDevice(String data) {
        rawMessage = data;
    }

    @Override
    public Collection<String> buildMessages() {
        return Collections.singleton(rawMessage);
    }

    @Override
    public ThingTypeUID getThingType() {
        return null;
    }

}
