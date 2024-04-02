package com.sample.impl.sensor.windowssys;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.module.ModuleRegistry;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataArrayImpl;
import org.vast.swe.SWEHelper;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import org.sensorhub.api.service.ServiceConfig;

import java.util.Timer;
import java.util.TimerTask;

public class Alerts extends AbstractSensorOutput<SystemsInfoSensor> implements Runnable {

    // order of operations
// deteremine required alerts
// create structure to pull alerts from other outputs to here
// set those alert values as local variables
// connect this datastream to the system
// add these observations to that datastream in a way that allows for the desired function.
// THings to do in order to acomplish this.
// find out how smlhelper functions in relation to all this
// find out if the mechanizm that is sending the alert is on the node side or the java code side, or both.
    OSProcess cpuVal;
    SystemInfo si = new SystemInfo();
    OperatingSystem os = si.getOperatingSystem();
    CentralProcessor processor = si.getHardware().getProcessor();

    private static final String SENSOR_OUTPUT_NAME = "Systems info alerts";
    private static final String SENSOR_OUTPUT_LABEL = "Systems info alerts";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "Alerts derived from computer system info metrics";
    private static final Logger logger = LoggerFactory.getLogger(SystemsInfoOutput.class);
    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private DataRecord dataStruct;
    private final Object histogramLock = new Object();
    private DataEncoding dataEncoding;
    private Boolean stopProcessing = false;
    private Thread worker;
    private final Object processingLock = new Object();
    private int setCount = 0;


    Alerts(SystemsInfoSensor parentSystemsInfoSensor) {

        super(SENSOR_OUTPUT_NAME, parentSystemsInfoSensor);

        logger.debug("Output created");


    }

    public void doInit() {
        logger.debug("Initializing Output");

        // Get an instance of SWE Factory suitable to build components
        SWEHelper sweFactory = new SWEHelper();

        dataStruct = sweFactory.createRecord()
                .name(SENSOR_OUTPUT_NAME)
                .label(SENSOR_OUTPUT_LABEL)
                .description(SENSOR_OUTPUT_DESCRIPTION)
                .addField("SampleTime", sweFactory.createTime().asSamplingTimeIsoUTC().build())
                .addField("cpuUsage", sweFactory.createText()
                        .label("Percent Usage of CPU")
                        .description("Percent Usage of the the CPU, Non-windows boxes may return above ")
                )


                .build();
        dataEncoding = sweFactory.newTextEncoding(",", "\n");
        logger.debug("Initializing Output Complete");

    }

    public void doStart() {

        // Instantiate a new worker thread
        worker = new Thread(this, this.name);

        // TODO: Perform other startup

        logger.info("Starting worker thread: {}", worker.getName());

        // Start the worker thread
        worker.start();

    }
    public void doStop() {

        synchronized (processingLock) {

            stopProcessing = true;
        }


    }
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




    public double setCpuVal() {
        double cpuLoad = processor.getSystemCpuLoad(1500)*100;

        return cpuLoad;

    }



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
                String cpuFormat = "System CPU Load: %.2f%%\n"+ setCpuVal();

                double timestamp = System.currentTimeMillis() / 1000d;


                parentSensor.getLogger().trace(String.format(String.valueOf(timestamp),cpuFormat));





                dataBlock.setDoubleValue(0, timestamp);
                dataBlock.setStringValue(1, cpuFormat);

                latestRecord = dataBlock;

                latestRecordTime = System.currentTimeMillis();

                eventHandler.publish(new DataEvent(latestRecordTime, Alerts.this, dataBlock));
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
