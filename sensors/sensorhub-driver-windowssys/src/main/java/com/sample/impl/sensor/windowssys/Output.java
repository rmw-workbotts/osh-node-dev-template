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

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.*;
import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;

/**
 * Output specification and provider for {@link Sensor}.
 *
 * @author Robin_White
 * @since 2/4/2024
 */
public class Output extends AbstractSensorOutput<Sensor> implements Runnable {

    private static final String SENSOR_OUTPUT_NAME = "Systems info";
    private static final String SENSOR_OUTPUT_LABEL = "Systems info";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "Metrics returned from computer system info";

    private static final Logger logger = LoggerFactory.getLogger(Output.class);

    private DataRecord dataStruct;
    private DataEncoding dataEncoding;

    private Boolean stopProcessing = false;
    private final Object processingLock = new Object();

    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private int setCount = 0;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private final Object histogramLock = new Object();

    private Thread worker;

    /**
     * Constructor
     *
     * @param parentSensor Sensor driver providing this output
     */
    Output(Sensor parentSensor) {

        super(SENSOR_OUTPUT_NAME, parentSensor);

        logger.debug("Output created");
    }

    /**
     * Initializes the data structure for the output, defining the fields, their ordering,
     * and data types.
     */
    void doInit() {

        logger.debug("Initializing Output");

        // Get an instance of SWE Factory suitable to build components
        SWEHelper sweFactory = new SWEHelper();

        // TODO: Create data record description
        dataStruct = sweFactory.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .description(SENSOR_OUTPUT_DESCRIPTION)
                .addField("sampleTime", sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Sample Time")
                        .description("Time of data collection"))
                .addField("Processors", sweFactory.createQuantity()
                        .definition(SWEHelper.getCfUri("Processor_#"))
                        .label("# of Processors"))
                .addField("freeMemory", sweFactory.createQuantity()
                        .label("Free Memory")
                        .description("Amount of Free Memory available to the the JVM")
                )
                .addField("totalMemory", sweFactory.createQuantity()
                        .label("Total Memory")
                        .description("Total Amount of Memory available to the the JVM at time of call")
                )
                .addField("sysOS", sweFactory.createText()
                        .label("Operating System")
                        .description("Runtime info on machines operating system")
                )
                .addField("Graphics_Devices", sweFactory.createText()
                        .label("Graphics Devices")
                        .description("Available Graphics Devices on system")
                )


                .build();

        dataEncoding = sweFactory.newTextEncoding(",", "\n");

        logger.debug("Initializing Output Complete");
    }

    /**
     * Begins processing data for output
     */
    public void doStart() {

        // Instantiate a new worker thread
        worker = new Thread(this, this.name);

        // TODO: Perform other startup

        logger.info("Starting worker thread: {}", worker.getName());

        // Start the worker thread
        worker.start();
    }

    /**
     * Terminates processing data for output
     */
    public void doStop() {

        synchronized (processingLock) {

            stopProcessing = true;
        }

        // TODO: Perform other shutdown procedures
    }

    /**
     * Check to validate data processing is still running
     *
     * @return true if worker thread is active, false otherwise
     */
    public boolean isAlive() {

        return worker.isAlive();
    }

    @Override
    public DataComponent getRecordDescription() {

        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {

        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {

        long accumulator = 0;

        synchronized (histogramLock) {

            for (int idx = 0; idx < MAX_NUM_TIMING_SAMPLES; ++idx) {

                accumulator += timingHistogram[idx];
            }
        }

        return accumulator / (double) MAX_NUM_TIMING_SAMPLES;
    }

    // Initalized variables for Observation outputs.
    double processor = 0;
    double memory = 0;

    double totalMem = 0;

    String Environ;
    String sysOs;

    // Functions to call for populating Observation outputs.

//    public abstract GraphicsConfiguration[] getConfigurations()
//    {
//
//    }
    Rectangle virtualBounds = new Rectangle();
    private Rectangle getEnviron()
    {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] gs =
            ge.getScreenDevices();
        for (int j = 0; j < gs.length; j++) {
        GraphicsDevice gd = gs[j];
        GraphicsConfiguration[] gc =
                gd.getConfigurations();
            for (int i=0; i < gc.length; i++) {
                virtualBounds =
                        virtualBounds.union(gc[i].getBounds());
            }
        }
        return virtualBounds;
    }
     private String getOS()
     {
        String l = System.getProperty("os.name").toLowerCase();
        return l;
     }
    private double getTotalMem()
    {
        double l = Runtime.getRuntime().totalMemory();
        return l;
    }
    private double getProcessors()
    {
       double l = Runtime.getRuntime().availableProcessors();
       return l;
    }
    private double getMemory()
    {
        double l = Runtime.getRuntime().freeMemory();
        return l;
    }

//public class Rectangle
//    {
//        private double length;
//        private double width;
//
//        /**
//         *  Constructor
//         */
//        public Rectangle(double l, double w)
//        {
//            length = l;
//            width  = w;
//        }
//
//        /**
//         * The overriding toString method returns the
//         * string containing object's length and width
//         */
//        public String toString()
//        {
//            return "Length : " + length + "\nWidth : " + width;
//        }
//    }
    @Override
    public void run() {

        boolean processSets = true;

        long lastSetTimeMillis = System.currentTimeMillis();

        try {

            while (processSets) {

                DataBlock dataBlock;
                if (latestRecord == null) {

                    dataBlock = dataStruct.createDataBlock();

                } else {

                    dataBlock = latestRecord.renew();
                }

                synchronized (histogramLock) {

                    int setIndex = setCount % MAX_NUM_TIMING_SAMPLES;

                    // Get a sampling time for latest set based on previous set sampling time
                    timingHistogram[setIndex] = System.currentTimeMillis() - lastSetTimeMillis;

                    // Set latest sampling time to now
                    lastSetTimeMillis = timingHistogram[setIndex];
                }

                ++setCount;

                double timestamp = System.currentTimeMillis() / 1000d;


                processor = getProcessors();
                memory = getMemory();
                totalMem = getTotalMem();
                sysOs = getOS();
                Environ = String.valueOf(getEnviron());
                parentSensor.getLogger().trace(String.format("processor=%4.2f, freeMem=%5.2f, totalMem=%3.1f, sysOs, Environ",
                        processor, memory, totalMem, sysOs, Environ));

                dataBlock.setDoubleValue(0, timestamp);
                dataBlock.setDoubleValue(1, processor);
                dataBlock.setDoubleValue(2, memory);
                dataBlock.setDoubleValue(3, totalMem);
                dataBlock.setStringValue(4, sysOs);
                dataBlock.setStringValue(5, Environ);



                latestRecord = dataBlock;

                latestRecordTime = System.currentTimeMillis();

                eventHandler.publish(new DataEvent(latestRecordTime, Output.this, dataBlock));

                synchronized (processingLock) {

                    processSets = !stopProcessing;
                }
            }

        } catch (Exception e) {

            logger.error("Error in worker thread: {}", Thread.currentThread().getName(), e);

        } finally {

            // Reset the flag so that when driver is restarted loop thread continues
            // until doStop called on the output again
            stopProcessing = false;

            logger.debug("Terminating worker thread: {}", this.name);
        }
    }
}