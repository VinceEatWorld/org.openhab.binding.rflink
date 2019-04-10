/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rflink.packet;

/**
 * RfLink packet type
 *
 * @author cartemere - Initial contribution
 */
public enum RfLinkPacketType {
    /** incoming packet from the Bridge (event) */
    INPUT,
    /** outgoing packet to the Bridge (command) */
    OUTPUT,
    /** feedback packet send to the bridge, but expected to be handled as an incoming packet */
    ECHO;

}
