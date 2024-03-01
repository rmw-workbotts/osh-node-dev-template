/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020-2021 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package com.sample.impl.sensor.windowssys;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//Curently calls for output2 make the sensor break upon start. Need to test if they work with just output2, or if it is specific to output2 rather than my formating.
//Pretty sure it's specific to output2. Something about the setup of the StorageOutput class is sending an Illegal charater in path error.
/**
 * Sensor driver providing sensor description, output registration, initialization and shutdown of driver and outputs.
 *
 * @author Robin White
 * @since 2/5/2024
 */
public class SystemsInfoSensor extends AbstractSensorModule<SystemsInfoConfig> {

    private static final Logger logger = LoggerFactory.getLogger(SystemsInfoSensor.class);

    SystemsInfoOutput output;
    StorageOutput output2;

    @Override
    public void doInit() throws SensorHubException {

        super.doInit();

        // Generate identifiers
        generateUniqueID("urn:osh:sensor:windowssys", config.serialNumber);
        generateXmlID("WINDOWS_SYSTEM_SENSOR", config.serialNumber);

        // Create and initialize output



        output = new SystemsInfoOutput(this);
        output2 = new StorageOutput(this);
        addOutput(output, false);
        addOutput(output2, false);


        output.doInit();
        output2.doInit();



    }

    @Override
    public void doStart() throws SensorHubException {

        if (null != output) {

            // Allocate necessary resources and start outputs
            output.doStart();

        }
        if (null != output2){


            output2.doStart();
        }

    }

    @Override
    public void doStop() throws SensorHubException {

        if (null != output) {

            output.doStop();

        }
        if (null != output2){


            output2.doStop();
        }



    }

    @Override
    public boolean isConnected() {

        // Determine if sensor is connected
        return output.isAlive();

    }

    public boolean isConnected2() {

        // Determine if sensor is connected
        return output2.isAlive();

    }
}
