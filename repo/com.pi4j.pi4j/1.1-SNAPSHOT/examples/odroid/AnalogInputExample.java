package odroid;
/*
 * #%L
 * **********************************************************************
 * ORGANIZATION  :  Pi4J
 * PROJECT       :  Pi4J :: Java Examples
 * FILENAME      :  AnalogInputExample.java
 *
 * This file is part of the Pi4J project. More information about
 * this project can be found here:  http://www.pi4j.com/
 * **********************************************************************
 * %%
 * Copyright (C) 2012 - 2016 Pi4J
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import com.pi4j.io.gpio.*;
import com.pi4j.platform.Platform;
import com.pi4j.platform.PlatformAlreadyAssignedException;
import com.pi4j.platform.PlatformManager;
import com.pi4j.util.Console;

/**
 * This example code demonstrates how to read the analog
 * int pins values from the Odroid C1/C1+/XU4 platform.
 *
 * @author Robert Savage
 */
public class AnalogInputExample {

    /**
     * @param args
     * @throws InterruptedException
     * @throws PlatformAlreadyAssignedException
     */
    public static void main(String[] args) throws InterruptedException, PlatformAlreadyAssignedException {

        // ####################################################################
        //
        // since we are not using the default Raspberry Pi platform, we should
        // explicitly assign the platform as the Odroid platform.
        //
        // ####################################################################
        PlatformManager.setPlatform(Platform.ODROID);

        // create Pi4J console wrapper/helper
        // (This is a utility class to abstract some of the boilerplate code)
        final Console console = new Console();

        // print program title/header
        console.title("<-- The Pi4J Project -->", "Analog Input Example");

        // create gpio controller
        final GpioController gpio = GpioFactory.getInstance();

        // ####################################################################
        //
        // IF YOU ARE USING AN ODROID C1/C1+ PLATFORM, THEN ...
        //    When provisioning a pin, use the OdroidC1Pin class.
        //
        // IF YOU ARE USING AN ODROID XU4 PLATFORM, THEN ...
        //    When provisioning a pin, use the OdroidXU4Pin class.
        //
        // ####################################################################

        // provision analog input pins
        final GpioPinAnalogInput[] inputs = {
                gpio.provisionAnalogInputPin(OdroidC1Pin.AIN0, "Analog Input 0"),
                gpio.provisionAnalogInputPin(OdroidC1Pin.AIN1, "Analog Input 1")
        };

        // set shutdown state for this pin: unexport the pins
        gpio.setShutdownOptions(true, inputs);

        // prompt user that we are ready
        console.println(" ... Successfully provisioned [" + inputs[0] + "]");
        console.println(" ... Successfully provisioned [" + inputs[1] + "]");
        console.emptyLine();
        console.box("Below is the 10-bit conversion value (a number ",
                    "between 0 and 1023) from the two analog input ",
                    "pins which represents a voltage applied to each",
                    "pin between 0VDC (Ground) and +1.8VDC.  If no ",
                    "voltage is currently applied to the analog input",
                    "pins then they may 'float' between a value of 0" ,
                    "to 1023.");

        // display current pin values
        console.emptyLine();
        console.println(" [" + inputs[0].toString() + "] value is: %4.0f (%2.1f VDC)",
                inputs[0].getValue(),
                getVoltage(inputs[0].getValue()));
        console.println(" [" + inputs[1].toString() + "] value is: %4.0f (%2.1f VDC)",
                inputs[1].getValue(),
                getVoltage(inputs[1].getValue()));
        console.emptyLine();

        // say goodbye
        console.goodbye();

        // stop all GPIO activity/threads by shutting down the GPIO controller
        // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
        gpio.shutdown();
    }

    /**
     * calculate relative analog input voltage based on the 10-bit conversion value
     * read from the hardware
     *
     * @param value 10-bit conversion value for analog input pin
     * @return relative voltage for analog input pin
     */
    private static double getVoltage(double value){
        return (value / 1024) * 1.8f; // 1.8VDC maximum allowed voltage per the hardware spec
    }
}
